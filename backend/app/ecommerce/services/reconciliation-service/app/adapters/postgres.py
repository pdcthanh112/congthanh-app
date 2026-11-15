"""Driven adapter Store trên Postgres (asyncpg). Event phát ra ghi outbox cùng transaction."""

from __future__ import annotations

import json
from datetime import UTC, date, datetime, time, timedelta
from importlib import resources
from typing import Any

import asyncpg
from ecommerce.common.v1 import event_envelope_pb2, money_pb2
from ecommerce.reconciliation.v1 import reconciliation_events_pb2 as rec
from ecommerce_platform.ids import event_id
from google.protobuf import timestamp_pb2
from google.protobuf.message import Message

from ..domain.matching import InternalTxn, Kind, MatchResult, ProviderTxn

SOURCE = "reconciliation-service"
AGGREGATE_TYPE = "reconciliation"
CONSUMER = "reconciliation-service"
ZERO_DECIMAL = ("JPY", "KRW", "VND")


def money(minor: int | None, currency: str) -> money_pb2.Money:
    if minor is None:
        return money_pb2.Money(currency_code=currency)
    scale = 1 if currency in ZERO_DECIMAL else 100
    units, rest = divmod(minor, scale)
    return money_pb2.Money(currency_code=currency, units=units, nanos=rest * (10**9 // scale))


def to_minor(m: money_pb2.Money) -> int:
    digits = 0 if m.currency_code in ZERO_DECIMAL else 2
    return m.units * 10**digits + round(m.nanos / 10 ** (9 - digits))


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


def _day_bounds(day: date) -> tuple[datetime, datetime]:
    start = datetime.combine(day, time.min, tzinfo=UTC)
    return start, start + timedelta(days=1)


class PostgresStore:
    def __init__(self, pool: asyncpg.Pool):
        self.pool = pool

    async def ping(self) -> None:
        async with self.pool.acquire() as c:
            await c.execute("SELECT 1")

    async def once(self, c: asyncpg.Connection, event_id: str) -> bool:
        r = await c.execute(
            "INSERT INTO processed_events (event_id, consumer) VALUES ($1,$2) ON CONFLICT DO NOTHING",
            event_id,
            CONSUMER,
        )
        return r != "INSERT 0 0"

    async def record_internal(self, c: asyncpg.Connection, event_id: str, t: InternalTxn) -> None:
        await c.execute(
            """INSERT INTO internal_txns (event_id, provider, provider_ref, kind, amount_minor, currency, order_id, at)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT DO NOTHING""",
            event_id,
            t.provider,
            t.ref,
            t.kind.value,
            t.amount_minor,
            t.currency,
            t.order_id,
            t.at,
        )

    async def internal_for_day(self, provider: str, day: date) -> list[InternalTxn]:
        start, end = _day_bounds(day)
        rows = await self.pool.fetch(
            """SELECT provider, provider_ref, kind, amount_minor, currency, order_id, at FROM internal_txns
               WHERE provider = $1 AND at >= $2 AND at < $3""",
            provider,
            start,
            end,
        )
        return [
            InternalTxn(
                r["provider"],
                r["provider_ref"],
                Kind(r["kind"]),
                r["amount_minor"],
                r["currency"],
                r["order_id"],
                r["at"],
            )
            for r in rows
        ]

    async def save_run(
        self, run_id: str, provider: str, day: date, source: str, txns: list[ProviderTxn], result: MatchResult
    ) -> None:
        async with self.pool.acquire() as c, c.transaction():
            old = await c.fetchval(
                "UPDATE runs SET superseded = true WHERE provider = $1 AND business_date = $2 AND NOT superseded RETURNING id",
                provider,
                day,
            )
            if old:
                await c.execute(
                    "UPDATE mismatches SET status = 'SUPERSEDED' WHERE run_id = $1 AND status = 'OPEN'", old
                )
            await c.execute(
                """INSERT INTO runs (id, provider, business_date, source, provider_transactions, matched, mismatched,
                   provider_fees_minor, currency) VALUES ($1::text::bigint,$2,$3,$4,$5,$6,$7,$8,$9)""",
                run_id,
                provider,
                day,
                source,
                len(txns),
                result.matched,
                len(result.mismatches),
                result.provider_fees_minor,
                result.currency,
            )
            await c.executemany(
                """INSERT INTO provider_txns (run_id, provider_ref, kind, amount_minor, fee_minor, currency, created_at, payout_id)
                   VALUES ($1::text::bigint,$2,$3,$4,$5,$6,$7,$8)""",
                [
                    (run_id, t.ref, t.kind.value, t.amount_minor, t.fee_minor, t.currency, t.created_at, t.payout_id)
                    for t in txns
                ],
            )
            for m in result.mismatches:
                await c.execute(
                    """INSERT INTO mismatches (run_id, provider, kind, txn_kind, provider_ref, order_id,
                       provider_amount_minor, internal_amount_minor, currency) VALUES ($1::text::bigint,$2,$3,$4,$5,$6,$7,$8,$9)""",
                    run_id,
                    provider,
                    m.kind.value,
                    m.txn_kind.value,
                    m.ref,
                    m.order_id,
                    m.provider_amount_minor,
                    m.internal_amount_minor,
                    m.currency,
                )
                await write_outbox(
                    c,
                    run_id,
                    rec.ReconciliationMismatchDetected(
                        run_id=run_id,
                        provider=provider,
                        kind=m.kind.value,
                        provider_ref=m.ref,
                        order_id=m.order_id,
                        provider_amount=money(m.provider_amount_minor, m.currency),
                        internal_amount=money(m.internal_amount_minor, m.currency),
                    ),
                )
            await write_outbox(
                c,
                run_id,
                rec.ReconciliationRunCompleted(
                    run_id=run_id,
                    provider=provider,
                    business_date=day.isoformat(),
                    matched=result.matched,
                    mismatched=len(result.mismatches),
                    provider_fees=money(result.provider_fees_minor, result.currency),
                ),
            )

    async def runs(self, limit: int) -> list[dict[str, Any]]:
        rows = await self.pool.fetch(
            "SELECT * FROM runs WHERE NOT superseded ORDER BY business_date DESC, created_at DESC LIMIT $1", limit
        )
        return [_run_json(r) for r in rows]

    async def run(self, run_id: str) -> dict[str, Any] | None:
        try:
            r = await self.pool.fetchrow("SELECT * FROM runs WHERE id = $1::text::bigint", run_id)
        except asyncpg.DataError:
            return None
        if r is None:
            return None
        out = _run_json(r)
        out["mismatches"] = [
            _mismatch_json(m)
            for m in await self.pool.fetch("SELECT * FROM mismatches WHERE run_id = $1 ORDER BY id", r["id"])
        ]
        return out

    async def mismatches(self, status: str, limit: int) -> list[dict[str, Any]]:
        rows = await self.pool.fetch(
            "SELECT * FROM mismatches WHERE status = $1 ORDER BY created_at LIMIT $2", status, limit
        )
        return [_mismatch_json(m) for m in rows]

    async def resolve(self, mismatch_id: int, by: str, note: str) -> dict[str, Any] | None:
        r = await self.pool.fetchrow(
            """UPDATE mismatches SET status = 'RESOLVED', resolved_by = $2, resolution_note = $3, resolved_at = now()
               WHERE id = $1 AND status = 'OPEN' RETURNING *""",
            mismatch_id,
            by,
            note,
        )
        return _mismatch_json(r) if r else None


def _run_json(r: asyncpg.Record) -> dict[str, Any]:
    return {
        "id": str(r["id"]),
        "provider": r["provider"],
        "businessDate": r["business_date"].isoformat(),
        "source": r["source"],
        "providerTransactions": r["provider_transactions"],
        "matched": r["matched"],
        "mismatched": r["mismatched"],
        "providerFeesMinor": r["provider_fees_minor"],
        "currency": r["currency"],
        "createdAt": r["created_at"].isoformat(),
    }


def _mismatch_json(m: asyncpg.Record) -> dict[str, Any]:
    return {
        "id": m["id"],
        "runId": str(m["run_id"]),
        "provider": m["provider"],
        "kind": m["kind"],
        "transactionKind": m["txn_kind"],
        "providerRef": m["provider_ref"],
        "orderId": m["order_id"],
        "providerAmountMinor": m["provider_amount_minor"],
        "internalAmountMinor": m["internal_amount_minor"],
        "currency": m["currency"],
        "status": m["status"],
        "resolvedBy": m["resolved_by"],
        "resolutionNote": m["resolution_note"],
    }
