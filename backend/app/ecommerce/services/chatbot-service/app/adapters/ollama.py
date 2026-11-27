"""LLM adapter cho Ollama tự host (POST /api/chat, stream NDJSON)."""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from ..domain.chat import Message, text_of
from ..ports import LLMError, LLMEvent, TextDelta, TurnEnd


class OllamaLLM:
    def __init__(self, client: httpx.AsyncClient, model: str):
        self.client, self.model = client, model

    # Model tự host chưa dùng tool: service tìm sản phẩm trước và đưa vào system prompt.
    supports_tools = False

    async def turn(self, system: str, messages: list[Message], tools: list[dict[str, Any]]) -> AsyncIterator[LLMEvent]:
        parts: list[str] = []
        async for text in self._stream(system, messages):
            parts.append(text)
            yield TextDelta(text)
        yield TurnEnd([{"type": "text", "text": "".join(parts)}], "end_turn")

    async def _stream(self, system: str, messages: list[Message]) -> AsyncIterator[str]:
        body = {
            "model": self.model,
            "stream": True,
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": text_of(m.content)} for m in messages],
        }
        try:
            async with self.client.stream("POST", "/api/chat", json=body) as r:
                if r.status_code != 200:
                    raise LLMError(f"local model error ({r.status_code})")
                async for line in r.aiter_lines():
                    if not line:
                        continue
                    chunk = json.loads(line)
                    if err := chunk.get("error"):
                        raise LLMError(f"local model error: {err}")
                    if text := chunk.get("message", {}).get("content"):
                        yield text
                    if chunk.get("done"):
                        return
        except httpx.HTTPError as e:
            raise LLMError("the assistant is unavailable right now") from e
