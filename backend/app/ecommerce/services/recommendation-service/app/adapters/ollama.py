"""Embedder qua Ollama (/api/embed), ví dụ model đa ngôn ngữ bge-m3 (1024 chiều)."""

from __future__ import annotations

import httpx


class OllamaEmbedder:
    def __init__(self, url: str, model: str, dim: int, timeout: float = 30.0):
        self.client = httpx.AsyncClient(base_url=url, timeout=timeout)
        self.model = model
        self.dim = dim

    async def embed(self, items: list[tuple[str, str]]) -> list[list[float]]:
        if not items:
            return []
        r = await self.client.post("/api/embed", json={"model": self.model, "input": [name for name, _ in items]})
        r.raise_for_status()
        out = r.json()["embeddings"]
        if out and len(out[0]) != self.dim:
            raise ValueError(f"model {self.model} returns {len(out[0])} dimensions, EMBEDDING_DIM is {self.dim}")
        return out

    async def close(self) -> None:
        await self.client.aclose()
