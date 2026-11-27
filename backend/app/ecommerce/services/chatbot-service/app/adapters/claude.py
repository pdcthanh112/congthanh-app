"""LLM adapter dùng Claude (Messages API, stream, tool use)."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from typing import Any

import anthropic

from ..domain.chat import Message
from ..ports import LLMError, LLMEvent, TextDelta, ToolCall, TurnEnd

log = logging.getLogger(__name__)


class ClaudeLLM:
    supports_tools = True

    def __init__(self, client: anthropic.AsyncAnthropic, model: str, effort: str, max_tokens: int):
        self.client, self.model, self.effort, self.max_tokens = client, model, effort, max_tokens

    async def turn(self, system: str, messages: list[Message], tools: list[dict[str, Any]]) -> AsyncIterator[LLMEvent]:
        params: dict[str, Any] = {
            "model": self.model,
            "max_tokens": self.max_tokens,
            # Prompt cache: system + tools cố định trong phiên, lịch sử chỉ nối thêm nên mỗi lượt đọc lại phần đã cache.
            "system": [{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}],
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "output_config": {"effort": self.effort},
            # Model chính từ chối vì bộ lọc an toàn thì API tự chuyển sang model dự phòng phù hợp.
            "betas": ["server-side-fallback-2026-07-01"],
            "fallbacks": "default",
        }
        if tools:
            params["tools"] = tools
        try:
            async with self.client.beta.messages.stream(**params) as stream:
                async for text in stream.text_stream:
                    yield TextDelta(text)
                final = await stream.get_final_message()
        except anthropic.RateLimitError as e:
            raise LLMError("the assistant is busy, please try again shortly") from e
        except anthropic.APIConnectionError as e:
            raise LLMError("the assistant is unavailable right now") from e
        except anthropic.APIStatusError as e:
            log.error("anthropic error", extra={"x_status": e.status_code, "x_request_id": e.request_id})
            raise LLMError("the assistant is unavailable right now") from e

        content = [b.model_dump(mode="json", exclude_none=True) for b in final.content]
        calls = [
            ToolCall(b.id, b.name, b.input if isinstance(b.input, dict) else {})
            for b in final.content
            if b.type == "tool_use"
        ]
        usage = final.usage
        log.info(
            "llm turn",
            extra={
                "x_stop_reason": final.stop_reason,
                "x_input_tokens": usage.input_tokens,
                "x_cache_read_tokens": getattr(usage, "cache_read_input_tokens", None),
                "x_output_tokens": usage.output_tokens,
                "x_tool_calls": len(calls),
            },
        )
        yield TurnEnd(content, final.stop_reason or "end_turn", calls)
