"""chatbot-service: trợ lý mua sắm (thay bản Java Spring AI). SSE: POST /api/ecommerce/chatbot/chat."""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

import anthropic
import httpx
from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import Principal, verifier_from_env
from ecommerce_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from .adapters.claude import ClaudeLLM
from .adapters.ollama import OllamaLLM
from .adapters.redis_history import RedisHistory
from .adapters.search import SearchServiceClient
from .adapters.vllm import VllmLLM
from .config import settings
from .domain.chat import InvalidRequest
from .service import ChatService, Event

eplog.setup("chatbot-service")
auth = Auth(verifier_from_env())


@asynccontextmanager
async def lifespan(app: FastAPI):
    history = RedisHistory(settings.redis_url)
    search_http = httpx.AsyncClient(base_url=settings.search_url, timeout=3.0)
    closers = [history.close, search_http.aclose]
    if settings.llm_provider == "ollama":
        ollama_http = httpx.AsyncClient(base_url=settings.ollama_url, timeout=httpx.Timeout(120.0, connect=3.0))
        llm = OllamaLLM(ollama_http, settings.ollama_model)
        closers.append(ollama_http.aclose)
    elif settings.llm_provider == "vllm":
        vllm_http = httpx.AsyncClient(base_url=settings.vllm_url, timeout=httpx.Timeout(120.0, connect=3.0))
        llm = VllmLLM(vllm_http, settings.vllm_model, settings.vllm_path, settings.max_output_tokens)
        closers.append(vllm_http.aclose)
    else:
        client = anthropic.AsyncAnthropic(max_retries=2, timeout=120.0)
        llm = ClaudeLLM(client, settings.anthropic_model, settings.anthropic_effort, settings.max_output_tokens)
        closers.append(client.close)
    app.state.history = history
    app.state.service = ChatService(
        llm,
        SearchServiceClient(search_http),
        history,
        store=settings.store_name,
        context_size=settings.product_context_size,
        turns=settings.history_turns,
        ttl=settings.history_ttl_seconds,
        max_chars=settings.max_message_chars,
    )
    yield
    for close in closers:
        await close()


app = FastAPI(
    title="chatbot-service",
    lifespan=lifespan,
    docs_url="/api/ecommerce/chatbot/docs",
    openapi_url="/api/ecommerce/chatbot/openapi.json",
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.history.ping()))


class ChatBody(BaseModel):
    message: str
    # Client tự sinh (vd. crypto.randomUUID()) để giữ ngữ cảnh nhiều lượt; bỏ trống = hỏi một lần.
    session_id: str | None = Field(default=None, alias="sessionId")


def service() -> ChatService:
    return app.state.service


def _sse(e: Event) -> str:
    return f"event: {e.type}\ndata: {json.dumps(e.data, ensure_ascii=False)}\n\n"


# Khách vãng lai cũng dùng được (gateway giới hạn tần suất theo IP); đăng nhập thì lịch sử gắn với tài khoản.
@app.post("/api/ecommerce/chatbot/chat")
async def chat(
    body: ChatBody,
    principal: Annotated[Principal | None, Depends(auth.optional)],
    svc: Annotated[ChatService, Depends(service)],
):
    try:
        text = svc.prepare(body.message, body.session_id)
    except InvalidRequest as e:
        raise HTTPException(400, str(e)) from e
    subject = principal.subject if principal else None

    async def events() -> AsyncIterator[str]:
        async for e in svc.chat(text, body.session_id, subject):
            yield _sse(e)

    # X-Accel-Buffering: không để proxy (nginx/APISIX) gom buffer làm mất hiệu ứng stream.
    return StreamingResponse(
        events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )


@app.delete("/api/ecommerce/chatbot/sessions/{session_id}")
async def reset(
    session_id: str,
    principal: Annotated[Principal | None, Depends(auth.optional)],
    svc: Annotated[ChatService, Depends(service)],
):
    try:
        await svc.reset(session_id, principal.subject if principal else None)
    except InvalidRequest as e:
        raise HTTPException(400, str(e)) from e
    return ok(None, "Chat session cleared")
