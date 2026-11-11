"""Driven adapter Store trên Postgres (asyncpg). Event phát ra ghi outbox cùng transaction."""

from __future__ import annotations

import json
from datetime import datetime, timedelta
from importlib import resources
from typing import Any

import asyncpg
from ecommerce.common.v1 import event_envelope_pb2, money_pb2
from ecommerce.fraud.v1 import fraud_events_pb2
from google.protobuf import timestamp_pb2
from google.protobuf.message import Message

from ..domain.rules import Assessment, Decision, History, Transaction
from ..ports import StoredAssessment
from ..service import Conflict, NotFound, uuid7

SOURCE = "fraud-service"
AGGREGATE_TYPE = "fraud"
CONSUMER = "fraud-service"


def money(minor: int, currency: str) -> money_pb2.Money:
    """Money{units, nanos} từ đơn vị nhỏ nhất (VND/JPY/KRW không có phần lẻ)."""
    digits = 0 if currency in ("VND", "JPY", "KRW") else 2
    scale = 10**digits
    units, rest = divmod(minor, scale)
    return money_pb2.Money(currency_code=currency, units=units, nanos=rest * (10**9 // scale))


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
        url, min_size=1, max_size=10, server_settings={"search_path": schema}, init=init, command_timeout=5
    )
    await migrate(pool, schema)
    return pool


async def write_outbox(c: asyncpg.Connection, aggregate_id: str, payload: Message) -> None:
    env = event_envelope_pb2.EventEnvelope(
        id=uuid7(),
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


_SELECT = """SELECT a.id::text, a.checkout_id, a.customer_id, a.order_id, a.amount_minor, a.currency, a.score, a.decision,
    a.reasons, r.status AS review_status, r.decided_by, r.note, a.created_at
    FROM assessments a LEFT JOIN reviews r ON r.assessment_id = a.id"""


def _row(r: asyncpg.Record) -> StoredAssessment:
    return StoredAssessment(
        id=r["id"],
        checkout_id=r["checkout_id"],
        customer_id=r["customer_id"],
        order_id=r["order_id"],
        amount=r["amount_minor"],
        currency=r["currency"],
        score=r["score"],
        decision=r["decision"],
        reasons=r["reasons"],
        review_status=r["review_status"],
        reviewed_by=r["decided_by"],
        review_note=r["note"],
        created_at=r["created_at"],
    )


class PostgresStore:
    def __init__(self, pool: asyncpg.Pool):
        self.pool = pool

    async def ping(self) -> None:
        await self.pool.fetchval("SELECT 1")

    async def history(self, customer_id: str, address_fp: str | None, now: datetime) -> History:
        r = await self.pool.fetchrow(
            """SELECT
                count(*) FILTER (WHERE status = 'PAID') AS paid,
                COALESCE(avg(amount_minor) FILTER (WHERE status = 'PAID'), 0)::bigint AS avg_paid,
                count(*) AS orders,
                count(*) FILTER (WHERE shipment_returned) AS shipments_returned,
                COALESCE(bool_or(address_fp = $2), false) AS known_address,
                (SELECT count(*) FROM payment_failures WHERE customer_id = $1 AND failed_at >= $3) AS failures,
                (SELECT count(*) FROM refunds WHERE customer_id = $1 AND refunded_at >= $4) AS refunds
               FROM customer_orders WHERE customer_id = $1""",
            customer_id,
            address_fp,
            now - timedelta(hours=24),
            now - timedelta(days=90),
        )
        return History(
            paid_orders=r["paid"],
            avg_paid_amount=r["avg_paid"],
            orders=r["orders"],
            shipments_returned=r["shipments_returned"],
            refunds_90d=r["refunds"],
            payment_failures_24h=r["failures"],
            known_address=r["known_address"],
        )

    async def blocked(self, values: dict[str, str]) -> list[str]:
        if not values:
            return []
        rows = await self.pool.fetch(
            "SELECT kind FROM blocklist b JOIN unnest($1::text[], $2::text[]) AS v(kind, value) USING (kind, value)",
            list(values.keys()),
            list(values.values()),
        )
        return sorted(r["kind"] for r in rows)

    async def save_assessment(self, id: str, tx: Transaction, a: Assessment, signals: dict[str, Any]) -> None:
        reasons = [{"code": h.code, "points": h.points, "detail": h.detail} for h in a.hits]
        async with self.pool.acquire() as c, c.transaction():
            await c.execute(
                """INSERT INTO assessments (id, checkout_id, customer_id, amount_minor, currency, payment_method, score, decision, reasons, signals)
                   VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)""",
                id,
                tx.checkout_id,
                tx.customer_id,
                tx.amount,
                tx.currency,
                tx.payment_method.value,
                a.score,
                a.decision.value,
                reasons,
                signals,
            )
            if a.decision == Decision.REVIEW:
                await c.execute("INSERT INTO reviews (assessment_id) VALUES ($1)", id)
                await write_outbox(
                    c,
                    tx.checkout_id,
                    fraud_events_pb2.FraudReviewRequested(
                        assessment_id=id,
                        checkout_id=tx.checkout_id,
                        customer_id=tx.customer_id,
                        amount=money(tx.amount, tx.currency),
                        score=a.score,
                        reasons=a.reasons,
                    ),
                )

    async def get_assessment(self, id_or_checkout: str) -> StoredAssessment | None:
        r = await self.pool.fetchrow(
            _SELECT
            + " WHERE a.id::text = $1 OR a.checkout_id = $1 OR a.order_id = $1 ORDER BY a.created_at DESC LIMIT 1",
            id_or_checkout,
        )
        return _row(r) if r else None

    async def list_reviews(self, status: str, limit: int, offset: int) -> list[StoredAssessment]:
        rows = await self.pool.fetch(
            _SELECT + " WHERE r.status = $1 ORDER BY r.created_at LIMIT $2 OFFSET $3", status, limit, offset
        )
        return [_row(r) for r in rows]

    async def decide_review(self, assessment_id: str, decision: str, by: str, note: str) -> StoredAssessment:
        status = "APPROVED" if decision == "APPROVE" else "REJECTED"
        async with self.pool.acquire() as c, c.transaction():
            r = await c.fetchrow(
                """SELECT r.status, a.checkout_id, a.customer_id FROM reviews r JOIN assessments a ON a.id = r.assessment_id
                   WHERE r.assessment_id::text = $1 FOR UPDATE OF r""",
                assessment_id,
            )
            if r is None:
                raise NotFound(f"review {assessment_id} not found")
            if r["status"] != "PENDING":
                raise Conflict(f"review is already {r['status']}")
            await c.execute(
                "UPDATE reviews SET status=$2, decided_by=$3, note=$4, decided_at=now() WHERE assessment_id::text=$1",
                assessment_id,
                status,
                by,
                note,
            )
            await write_outbox(
                c,
                r["checkout_id"],
                fraud_events_pb2.FraudReviewDecided(
                    assessment_id=assessment_id,
                    checkout_id=r["checkout_id"],
                    customer_id=r["customer_id"],
                    decision=decision,
                    decided_by=by,
                    note=note,
                ),
            )
            row = await c.fetchrow(_SELECT + " WHERE a.id::text = $1", assessment_id)
        return _row(row)

    async def block(self, kind: str, value: str, reason: str, by: str) -> None:
        await self.pool.execute(
            """INSERT INTO blocklist (kind, value, reason, created_by) VALUES ($1,$2,$3,$4)
               ON CONFLICT (kind, value) DO UPDATE SET reason = EXCLUDED.reason, created_by = EXCLUDED.created_by""",
            kind,
            value,
            reason,
            by,
        )

    async def unblock(self, kind: str, value: str) -> bool:
        return await self.pool.execute("DELETE FROM blocklist WHERE kind=$1 AND value=$2", kind, value) != "DELETE 0"

    async def list_blocks(self, limit: int) -> list[dict[str, Any]]:
        rows = await self.pool.fetch(
            "SELECT kind, value, reason, created_by, created_at FROM blocklist ORDER BY created_at DESC LIMIT $1", limit
        )
        return [
            {
                "kind": r["kind"],
                "value": r["value"],
                "reason": r["reason"],
                "createdBy": r["created_by"],
                "createdAt": r["created_at"].isoformat(),
            }
            for r in rows
        ]

    # ---------- nhãn từ event (gọi trong transaction của consumer) ----------

    async def once(self, c: asyncpg.Connection, event_id: str) -> bool:
        """True nếu event chưa xử lý (đánh dấu luôn trong cùng transaction)."""
        r = await c.execute(
            "INSERT INTO processed_events (event_id, consumer) VALUES ($1,$2) ON CONFLICT DO NOTHING",
            event_id,
            CONSUMER,
        )
        return r != "INSERT 0 0"

    async def order_placed(
        self,
        c: asyncpg.Connection,
        order_id: str,
        customer_id: str,
        amount: int,
        currency: str,
        checkout_id: str,
        address_fp: str | None,
    ) -> None:
        method = None
        if checkout_id:
            method = await c.fetchval(
                """UPDATE assessments SET order_id = $1 WHERE id = (
                     SELECT id FROM assessments WHERE checkout_id = $2 ORDER BY created_at DESC LIMIT 1) RETURNING payment_method""",
                order_id,
                checkout_id,
            )
        await c.execute(
            """INSERT INTO customer_orders (order_id, customer_id, amount_minor, currency, payment_method, address_fp)
               VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (order_id) DO NOTHING""",
            order_id,
            customer_id,
            amount,
            currency,
            method,
            address_fp,
        )

    async def set_status(self, c: asyncpg.Connection, order_id: str, status: str) -> None:
        await c.execute("UPDATE customer_orders SET status=$2, updated_at=now() WHERE order_id=$1", order_id, status)

    async def payment_failed(self, c: asyncpg.Connection, order_id: str, reason: str) -> None:
        await c.execute(
            """INSERT INTO payment_failures (customer_id, order_id, reason)
               SELECT customer_id, order_id, $2 FROM customer_orders WHERE order_id = $1""",
            order_id,
            reason[:500],
        )

    async def refunded(self, c: asyncpg.Connection, order_id: str) -> None:
        await c.execute(
            """INSERT INTO refunds (order_id, customer_id) SELECT order_id, customer_id FROM customer_orders WHERE order_id = $1
               ON CONFLICT DO NOTHING""",
            order_id,
        )
        await self.set_status(c, order_id, "REFUNDED")

    async def shipment_returned(self, c: asyncpg.Connection, order_id: str) -> None:
        await c.execute(
            "UPDATE customer_orders SET shipment_returned = TRUE, updated_at = now() WHERE order_id = $1",
            order_id,
        )
