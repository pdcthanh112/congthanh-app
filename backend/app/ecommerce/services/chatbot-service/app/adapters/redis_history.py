"""Lịch sử hội thoại trên Redis: mỗi phần tử list là một lượt (JSON các message của lượt đó)."""

from __future__ import annotations

import json

import redis.asyncio as redis

from ..domain.chat import Message, Turn


class RedisHistory:
    def __init__(self, url: str):
        self.r = redis.from_url(url, decode_responses=True, socket_timeout=1.0, socket_connect_timeout=1.0)

    async def load(self, key: str, turns: int) -> list[Turn]:
        raw = await self.r.lrange(key, -turns, -1)
        out: list[Turn] = []
        for x in raw:
            data = json.loads(x)
            # Bản cũ lưu từng message rời (không có "messages"): bỏ qua, phiên đó coi như mới.
            if isinstance(data, dict) and isinstance(data.get("messages"), list):
                out.append(Turn([Message(m["role"], m["content"]) for m in data["messages"]]))
        return out

    async def append(self, key: str, turn: Turn, keep_turns: int, ttl: int) -> None:
        entry = json.dumps({"messages": [m.__dict__ for m in turn.messages]}, ensure_ascii=False)
        async with self.r.pipeline(transaction=True) as p:
            p.rpush(key, entry)
            p.ltrim(key, -keep_turns, -1)
            p.expire(key, ttl)
            await p.execute()

    async def clear(self, key: str) -> None:
        await self.r.delete(key)

    async def ping(self) -> None:
        await self.r.ping()

    async def close(self) -> None:
        await self.r.aclose()
