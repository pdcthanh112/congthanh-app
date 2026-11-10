"""translate-service: dịch nội dung bằng Claude (thay bản Java gọi OpenAI). REST: POST /api/ecommerce/translate."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Annotated

import anthropic
from ecom_platform import logging as eplog
from ecom_platform.auth import Principal, verifier_from_env
from ecom_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field

from .adapters.claude import ClaudeTranslator, TranslatorUnavailable
from .adapters.valkey import ValkeyCache
from .config import settings
from .domain.translation import LANGUAGES, Format, InvalidRequest, TranslateRequest
from .service import TranslateService

eplog.setup("translate-service")
auth = Auth(verifier_from_env())


@asynccontextmanager
async def lifespan(app: FastAPI):
    cache = ValkeyCache(settings.valkey_url)
    client = anthropic.AsyncAnthropic(max_retries=2, timeout=60.0)
    app.state.cache = cache
    app.state.service = TranslateService(
        ClaudeTranslator(client, settings.anthropic_model, settings.anthropic_effort),
        cache,
        settings.cache_ttl_seconds,
        settings.max_texts,
        settings.max_chars,
    )
    yield
    await client.close()
    await cache.close()


app = FastAPI(
    title="translate-service",
    lifespan=lifespan,
    docs_url="/api/ecommerce/translate/docs",
    openapi_url="/api/ecommerce/translate/openapi.json",
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.cache.ping()))


class TranslateBody(BaseModel):
    texts: list[str] = Field(min_length=1)
    target_lang: str = Field(alias="targetLang")
    source_lang: str | None = Field(default=None, alias="sourceLang")
    format: Format = Format.TEXT


class LegacyBody(BaseModel):
    """Body của bản Java: {"language": "vi", "text": "..."}."""

    language: str
    text: str


def service() -> TranslateService:
    return app.state.service


async def _run(svc: TranslateService, req: TranslateRequest) -> list[str]:
    try:
        return await svc.translate(req)
    except InvalidRequest as e:
        raise HTTPException(400, str(e)) from e
    except TranslatorUnavailable as e:
        raise HTTPException(503, str(e)) from e


@app.get("/api/ecommerce/translate/languages")
async def languages():
    return ok([{"code": k, "name": v} for k, v in LANGUAGES.items()], "Get languages successfully")


# Cần đăng nhập: gọi LLM tốn tiền, không mở cho khách vãng lai (gateway còn giới hạn tần suất).
@app.post("/api/ecommerce/translate")
async def translate(
    body: TranslateBody,
    _: Annotated[Principal, Depends(auth.required)],
    svc: Annotated[TranslateService, Depends(service)],
):
    req = TranslateRequest(
        texts=tuple(body.texts), target=body.target_lang, source=body.source_lang, format=body.format
    )
    out = await _run(svc, req)
    return ok({"targetLang": body.target_lang, "translations": out}, "Translate successfully")


@app.post("/api/ecommerce/translate/legacy", deprecated=True)
async def translate_legacy(
    body: LegacyBody,
    _: Annotated[Principal, Depends(auth.required)],
    svc: Annotated[TranslateService, Depends(service)],
):
    out = await _run(svc, TranslateRequest(texts=(body.text,), target=body.language))
    return ok(out[0], "Translate successfully")
