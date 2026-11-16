"""ProductSearch qua REST của search-service (Elasticsearch)."""

from __future__ import annotations

import httpx

from ..domain.chat import Product


class SearchServiceClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client

    async def search(self, query: str, limit: int) -> list[Product]:
        r = await self.client.get("/api/ecommerce/search/products", params={"q": query[:200], "size": limit})
        r.raise_for_status()
        hits = (r.json().get("data") or {}).get("hits") or []
        return [
            Product(
                id=h["id"],
                name=h["name"],
                slug=h.get("slug", ""),
                price=float(h.get("price", 0)),
                currency=h["currency"],
            )
            for h in hits
            # Sản phẩm không kèm đơn vị tiền thì không đưa cho LLM: không đoán giá theo USD.
            if h.get("status", "ACTIVE") == "ACTIVE" and h.get("currency")
        ]
