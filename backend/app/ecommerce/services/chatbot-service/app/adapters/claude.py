"""LLM adapter dùng Claude, stream câu trả lời."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator

import anthropic

from ..domain.chat import Message
from ..ports import LLMError

log = logging.getLogger(__name__)


class ClaudeLLM:
    def __init__(self, client: anthropic.AsyncAnthropic, model: str, effort: str, max_tokens: int):
        self.client, self.model, self.effort, self.max_tokens = client, model, effort, max_tokens

    async def stream(self, system: str, messages: list[Message]) -> AsyncIterator[str]:
        try:
            # fallbacks="default": khi bộ lọc an toàn từ chối, API tự chạy lại trên model dự phòng.
            async with self.client.beta.messages.stream(
                model=self.model,
                max_tokens=self.max_tokens,
                system=system,
                messages=[{"role": m.role, "content": m.content} for m in messages],
                output_config={"effort": self.effort},
                betas=["server-side-fallback-2026-07-01"],
                fallbacks="default",
            ) as stream:
                async for text in stream.text_stream:
                    yield text
                final = await stream.get_final_message()
        except anthropic.RateLimitError as e:
            raise LLMError("the assistant is busy, please try again shortly") from e
        except anthropic.APIConnectionError as e:
            raise LLMError("the assistant is unavailable right now") from e
        except anthropic.APIStatusError as e:
            log.error("anthropic error", extra={"x_status": e.status_code, "x_request_id": e.request_id})
            raise LLMError("the assistant is unavailable right now") from e
        if final.stop_reason == "refusal":
            raise LLMError("the assistant can't help with that request")
