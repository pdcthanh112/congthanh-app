"""History trên Redis: mỗi phiên một list JSON, giữ N lượt gần nhất, hết hạn sau TTL."""

from __future__ import annotations

import json

import redis.asyncio as redis

from ..domain.chat import Message


class RedisHistory:
    def __init__(self, url: str):
        self.r = redis.from_url(url, decode_responses=True, socket_timeout=1.0, socket_connect_timeout=1.0)

    async def load(self, key: str, turns: int) -> list[Message]:
        raw = await self.r.lrange(key, -2 * turns, -1)
        return [Message(**json.loads(x)) for x in raw]

    async def append(self, key: str, messages: list[Message], keep_turns: int, ttl: int) -> None:
        async with self.r.pipeline(transaction=True) as p:
            p.rpush(key, *[json.dumps(m.__dict__, ensure_ascii=False) for m in messages])
            p.ltrim(key, -2 * keep_turns, -1)
            p.expire(key, ttl)
            await p.execute()

    async def clear(self, key: str) -> None:
        await self.r.delete(key)

    async def ping(self) -> None:
        await self.r.ping()

    async def close(self) -> None:
        await self.r.aclose()
