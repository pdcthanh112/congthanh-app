"""Driven adapter Store trên Postgres (asyncpg). ModerationDecided ghi outbox cùng transaction với quyết định."""

from __future__ import annotations

import json
from importlib import resources
from typing import Any

import asyncpg
from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.moderation.v1 import moderation_events_pb2 as mod
from ecommerce_platform.ids import event_id, snowflake
from google.protobuf import timestamp_pb2
from google.protobuf.message import Message

from ..domain.policy import Decision, Item, Verdict

SOURCE = "moderation-service"
AGGREGATE_TYPE = "moderation"


async def migrate(pool: asyncpg.Pool, schema: str) -> None:
    """Chạy migration SQL theo thứ tự tên file, mỗi file một transaction; advisory lock cho nhiều pod."""
    async with pool.acquire() as c:
        await c.execute(f'CREATE SCHEMA IF NOT EXISTS "{schema}"')
        await c.execute("SELECT pg_advisory_lock(hashtext($1))", f"{schema}.migrations")
        try:
            await c.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
            )
            done = {r["version"] for r in await c.fetch("SELECT version FROM schema_migrations")}
            files = sorted(
                (f for f in resources.files("app.migrations").iterdir() if f.name.endswith(".sql")),
                key=lambda f: f.name,
            )
            for f in files:
                if f.name in done:
                    continue
                async with c.transaction():
                    await c.execute(f.read_text())
                    await c.execute("INSERT INTO schema_migrations (version) VALUES ($1)", f.name)
        finally:
            await c.execute("SELECT pg_advisory_unlock(hashtext($1))", f"{schema}.migrations")


async def connect(url: str, schema: str) -> asyncpg.Pool:
    async def init(c: asyncpg.Connection) -> None:
        await c.set_type_codec("jsonb", encoder=json.dumps, decoder=json.loads, schema="pg_catalog")

    pool = await asyncpg.create_pool(
        url, min_size=1, max_size=10, server_settings={"search_path": schema}, init=init, command_timeout=30
    )
    await migrate(pool, schema)
    return pool


async def write_outbox(c: asyncpg.Connection, aggregate_id: str, payload: Message) -> None:
    env = event_envelope_pb2.EventEnvelope(
        id=event_id(),
        type=payload.DESCRIPTOR.full_name,
        source=SOURCE,
        aggregate_type=AGGREGATE_TYPE,
        aggregate_id=aggregate_id,
    )
    ts = timestamp_pb2.Timestamp()
    ts.GetCurrentTime()
    env.occurred_at.CopyFrom(ts)
    env.payload.Pack(payload)
    await c.execute(
        "INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1,$2,$3,$4,$5)",
        env.id,
        AGGREGATE_TYPE,
        aggregate_id,
        env.type,
        env.SerializeToString(),
    )


_COLUMNS = """id::text, content_type, content_id, author_id, status, decision, categories, redacted_text, rationale,
    decided_by, policy_version, appeal_reason, created_at, decided_at"""


def _json(r: asyncpg.Record, with_text: bool = False, text: str | None = None) -> dict[str, Any]:
    out = {
        "id": r["id"],
        "contentType": r["content_type"],
        "contentId": r["content_id"],
        "authorId": r["author_id"],
        "status": r["status"],
        "decision": r["decision"],
        "categories": r["categories"],
        "redactedText": r["redacted_text"],
        "rationale": r["rationale"],
        "decidedBy": r["decided_by"],
        "policyVersion": r["policy_version"],
        "appealReason": r["appeal_reason"],
        "createdAt": r["created_at"].isoformat(),
        "decidedAt": r["decided_at"].isoformat(),
    }
    if with_text:
        out["text"] = text
    return out


class PostgresStore:
    def __init__(self, pool: asyncpg.Pool, policy_version: str):
        self.pool = pool
        self.policy_version = policy_version

    async def ping(self) -> None:
        async with self.pool.acquire() as c:
            await c.execute("SELECT 1")

    async def cached_verdict(self, content_hash: str) -> dict[str, Any] | None:
        """Quyết định gần nhất cho nội dung giống hệt, cùng phiên bản chính sách (spam dán lại không tốn LLM)."""
        r = await self.pool.fetchrow(
            """SELECT decision, categories, redacted_text FROM items
               WHERE content_hash = $1 AND status = 'DECIDED' AND policy_version = $2
               ORDER BY decided_at DESC LIMIT 1""",
            content_hash,
            self.policy_version,
        )
        return dict(r) if r else None

    async def save_item(self, item: Item, content_hash: str, verdict: Verdict, decided_by: str) -> dict[str, Any]:
        status = "ESCALATED" if verdict.decision == Decision.ESCALATE else "DECIDED"
        cats = [c.value if hasattr(c, "value") else str(c) for c in verdict.categories]
        async with self.pool.acquire() as c, c.transaction():
            r = await c.fetchrow(
                f"""INSERT INTO items (id, content_type, content_id, author_id, text, content_hash, status, decision,
                    categories, redacted_text, rationale, decided_by, policy_version)
                    VALUES ($1::text::bigint,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
                    ON CONFLICT (content_type, content_id, content_hash) DO NOTHING RETURNING {_COLUMNS}""",
                snowflake(),
                item.content_type.value,
                item.content_id,
                item.author_id,
                item.text,
                content_hash,
                status,
                verdict.decision.value,
                cats,
                verdict.redacted_text,
                verdict.rationale,
                decided_by,
                self.policy_version,
            )
            if r is None:  # cùng nội dung gửi lại: trả quyết định cũ, không phát event lần hai
                r = await c.fetchrow(
                    f"SELECT {_COLUMNS} FROM items WHERE content_type = $1 AND content_id = $2 AND content_hash = $3",
                    item.content_type.value,
                    item.content_id,
                    content_hash,
                )
                return _json(r)
            await c.execute(
                "INSERT INTO decisions (item_id, decision, decided_by, note) VALUES ($1::text::bigint,$2,$3,$4)",
                r["id"],
                verdict.decision.value,
                decided_by,
                verdict.rationale,
            )
            await self._emit(c, r)
            return _json(r)

    async def _emit(self, c: asyncpg.Connection, r: asyncpg.Record) -> None:
        await write_outbox(
            c,
            f"{r['content_type']}:{r['content_id']}",
            mod.ModerationDecided(
                item_id=r["id"],
                content_type=r["content_type"],
                content_id=r["content_id"],
                author_id=r["author_id"],
                decision=r["decision"],
                categories=r["categories"],
                decided_by=r["decided_by"],
                policy_version=r["policy_version"],
                redacted_text=r["redacted_text"],
            ),
        )

    async def get(self, item_id: str) -> dict[str, Any] | None:
        try:
            r = await self.pool.fetchrow(f"SELECT {_COLUMNS}, text FROM items WHERE id = $1::text::bigint", item_id)
        except asyncpg.DataError:
            return None
        return _json(r, True, r["text"]) if r else None

    async def queue(self, limit: int) -> list[dict[str, Any]]:
        rows = await self.pool.fetch(
            f"""SELECT {_COLUMNS}, text FROM items WHERE status IN ('ESCALATED', 'APPEALED')
                ORDER BY (status = 'APPEALED') DESC, created_at LIMIT $1""",
            limit,
        )
        return [_json(r, True, r["text"]) for r in rows]

    async def decide(self, item_id: str, verdict: Verdict, by: str, note: str) -> dict[str, Any] | None:
        async with self.pool.acquire() as c, c.transaction():
            r = await c.fetchrow(
                f"""UPDATE items SET status = 'DECIDED', decision = $2, redacted_text = $3, rationale = $4,
                    decided_by = $5, decided_at = now()
                    WHERE id = $1::text::bigint AND status IN ('ESCALATED', 'APPEALED') RETURNING {_COLUMNS}""",
                item_id,
                verdict.decision.value,
                verdict.redacted_text,
                note,
                by,
            )
            if r is None:
                return None
            await c.execute(
                "INSERT INTO decisions (item_id, decision, decided_by, note) VALUES ($1::text::bigint,$2,$3,$4)",
                item_id,
                verdict.decision.value,
                by,
                note,
            )
            await self._emit(c, r)
            return _json(r)

    async def appeal(self, item_id: str, author_id: str, reason: str) -> dict[str, Any] | None:
        r = await self.pool.fetchrow(
            f"""UPDATE items SET status = 'APPEALED', appeal_reason = $3
                WHERE id = $1::text::bigint AND author_id = $2 AND status = 'DECIDED' AND decision IN ('REJECT', 'REDACT')
                  AND appeal_reason IS NULL RETURNING {_COLUMNS}""",
            item_id,
            author_id,
            reason,
        )
        return _json(r) if r else None

    async def rejections_since(self, author_id: str, days: int) -> int:
        return await self.pool.fetchval(
            "SELECT count(*) FROM items WHERE author_id = $1 AND decision = 'REJECT' AND decided_at > now() - make_interval(days => $2)",
            author_id,
            days,
        )

    async def issue_strike(self, author_id: str, strikes: int, item_id: str) -> None:
        async with self.pool.acquire() as c, c.transaction():
            await c.execute(
                """INSERT INTO strikes (author_id, strikes, last_item_id) VALUES ($1,$2,$3::text::bigint)
                   ON CONFLICT (author_id) DO UPDATE SET strikes = EXCLUDED.strikes, last_item_id = EXCLUDED.last_item_id,
                   issued_at = now()""",
                author_id,
                strikes,
                item_id,
            )
            await write_outbox(
                c, author_id, mod.UserContentStrikeIssued(author_id=author_id, strikes=strikes, last_item_id=item_id)
            )
