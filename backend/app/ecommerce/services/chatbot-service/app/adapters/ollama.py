"""LLM adapter cho Ollama tự host (POST /api/chat, stream NDJSON)."""

from __future__ import annotations

import json
from collections.abc import AsyncIterator

import httpx

from ..domain.chat import Message
from ..ports import LLMError


class OllamaLLM:
    def __init__(self, client: httpx.AsyncClient, model: str):
        self.client, self.model = client, model

    async def stream(self, system: str, messages: list[Message]) -> AsyncIterator[str]:
        body = {
            "model": self.model,
            "stream": True,
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": m.content} for m in messages],
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
