"""LLM adapter cho model tự host trên KServe + vLLM (API chat completions dạng OpenAI, stream SSE).

KServe runtime huggingface (backend vLLM) mở endpoint /openai/v1/chat/completions; vLLM chạy trần là /v1/...
nên đường dẫn đầy đủ để trong cấu hình (VLLM_PATH).
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator

import httpx

from ..domain.chat import Message
from ..ports import LLMError


class VllmLLM:
    def __init__(self, client: httpx.AsyncClient, model: str, path: str, max_tokens: int):
        self.client, self.model, self.path, self.max_tokens = client, model, path, max_tokens

    async def stream(self, system: str, messages: list[Message]) -> AsyncIterator[str]:
        body = {
            "model": self.model,
            "stream": True,
            "max_tokens": self.max_tokens,
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": m.content} for m in messages],
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
