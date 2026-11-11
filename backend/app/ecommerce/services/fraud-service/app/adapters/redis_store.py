"""Driven adapter trên Redis. VelocityCounter: sorted set theo cửa sổ trượt, member là id duy nhất
(checkout lặp lại không bị đếm hai lần). Giá trị nhạy cảm (IP, thẻ, địa chỉ) được băm trước khi làm khoá."""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime

import redis.asyncio as redis

from ..domain.rules import StreamFeatures, Transaction, Velocity

HOUR = 3600
DAY = 24 * HOUR
WEEK = 7 * DAY


def h(v: str) -> str:
    return hashlib.sha256(v.encode()).hexdigest()[:24]


class RedisVelocity:
    def __init__(self, url: str, prefix: str = "fraud"):
        self.r = redis.from_url(url, decode_responses=True, socket_timeout=0.2, socket_connect_timeout=0.2)
        self.prefix = prefix

    async def record_and_count(self, tx: Transaction, now: datetime) -> Velocity:
        ts = now.timestamp()
        # (khoá, member, cửa sổ giây); None = không có dữ liệu để đếm.
        specs: list[tuple[str, str, int] | None] = [
            (f"{self.prefix}:cust:{tx.customer_id}:co", tx.checkout_id, HOUR),
            (f"{self.prefix}:ip:{h(tx.ip)}:co", tx.checkout_id, HOUR) if tx.ip else None,
            (f"{self.prefix}:dev:{h(tx.device_id)}:cust", tx.customer_id, DAY) if tx.device_id else None,
            (f"{self.prefix}:cust:{tx.customer_id}:card", h(tx.card_fingerprint), DAY) if tx.card_fingerprint else None,
            (f"{self.prefix}:cust:{tx.customer_id}:addr", h(tx.shipping.fingerprint()), WEEK) if tx.shipping else None,
        ]
        async with self.r.pipeline(transaction=True) as p:
            for s in specs:
                if s is None:
                    continue
                key, member, window = s
                p.zadd(key, {member: ts})
                p.zremrangebyscore(key, "-inf", ts - window)
                p.zcard(key)
                p.expire(key, window)
            res = await p.execute()
        counts: list[int] = []
        i = 0
        for s in specs:
            if s is None:
                counts.append(0)
            else:
                counts.append(int(res[i + 2]))
                i += 4
        return Velocity(
            customer_checkouts_1h=counts[0],
            ip_checkouts_1h=counts[1],
            device_customers_24h=counts[2],
            customer_cards_24h=counts[3],
            customer_addresses_7d=counts[4],
        )

    async def ping(self) -> None:
        await self.r.ping()

    async def close(self) -> None:
        await self.r.aclose()


class RedisStreamFeatures:
    """StreamFeatureStore: mỗi khách một key JSON, hết hạn sau 24 giờ (feature 1 giờ/24 giờ không còn ý nghĩa)."""

    def __init__(self, r: redis.Redis, prefix: str = "fraud"):
        self.r = r
        self.prefix = prefix

    def _key(self, customer_id: str) -> str:
        return f"{self.prefix}:stream:cust:{customer_id}"

    async def put(self, customer_id: str, f: StreamFeatures) -> None:
        doc = {**f.__dict__, "computed_at": f.computed_at.isoformat() if f.computed_at else None}
        await self.r.set(self._key(customer_id), json.dumps(doc), ex=DAY)

    async def get(self, customer_id: str) -> StreamFeatures | None:
        raw = await self.r.get(self._key(customer_id))
        if not raw:
            return None
        d = json.loads(raw)
        at = d.pop("computed_at", None)
        known = {k: v for k, v in d.items() if k in StreamFeatures.__dataclass_fields__}
        return StreamFeatures(**known, computed_at=datetime.fromisoformat(at).astimezone(UTC) if at else None)
