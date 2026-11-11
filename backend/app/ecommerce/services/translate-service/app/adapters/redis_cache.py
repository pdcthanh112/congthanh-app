"""Driven adapter Cache trên Redis (giao thức Redis)."""

from __future__ import annotations

import redis.asyncio as redis


class RedisCache:
    def __init__(self, url: str):
        self.r = redis.from_url(url, decode_responses=True, socket_timeout=1.0, socket_connect_timeout=1.0)

    async def get_many(self, keys: list[str]) -> list[str | None]:
        return await self.r.mget(keys) if keys else []

    async def set_many(self, items: dict[str, str], ttl: int) -> None:
        if not items:
            return
        async with self.r.pipeline(transaction=False) as p:
            for k, v in items.items():
                p.set(k, v, ex=ttl)
            await p.execute()

    async def ping(self) -> None:
        await self.r.ping()

    async def close(self) -> None:
        await self.r.aclose()
