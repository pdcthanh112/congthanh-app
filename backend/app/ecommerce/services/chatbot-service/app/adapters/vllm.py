"""LLM adapter cho model tự host trên KServe + vLLM (API chat completions dạng OpenAI, stream SSE).

KServe runtime huggingface (backend vLLM) mở endpoint /openai/v1/chat/completions; vLLM chạy trần là /v1/...
nên đường dẫn đầy đủ để trong cấu hình (VLLM_PATH).
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from ..domain.chat import Message, text_of
from ..ports import LLMError, LLMEvent, TextDelta, TurnEnd


class VllmLLM:
    def __init__(self, client: httpx.AsyncClient, model: str, path: str, max_tokens: int):
        self.client, self.model, self.path, self.max_tokens = client, model, path, max_tokens

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
            "max_tokens": self.max_tokens,
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": text_of(m.content)} for m in messages],
        }
        try:
            async with self.client.stream("POST", self.path, json=body) as r:
                if r.status_code != 200:
                    raise LLMError(f"local model error ({r.status_code})")
                async for line in r.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        return
                    chunk = json.loads(data)
                    if err := chunk.get("error"):
                        raise LLMError(f"local model error: {err.get('message', err)}")
                    for choice in chunk.get("choices") or []:
                        if text := (choice.get("delta") or {}).get("content"):
                            yield text
        except httpx.HTTPError as e:
            raise LLMError("the assistant is unavailable right now") from e
