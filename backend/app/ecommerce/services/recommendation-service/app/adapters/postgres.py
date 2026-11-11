"""Driven adapter Store (asyncpg) và VectorIndex trên pgvector."""

from __future__ import annotations

from datetime import datetime, timedelta
from importlib import resources
from itertools import combinations

import asyncpg

from ..domain.ranking import Kind, Reason, Scored

CONSUMER = "recommendation-service"
MAX_ITEMS_PER_ORDER = 30  # giới hạn số cặp (n²) từ một đơn rất lớn


async def migrate(pool: asyncpg.Pool, schema: str) -> None:
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
    # public trong search_path để dùng kiểu "vector" của extension (cài ở schema public).
    pool = await asyncpg.create_pool(
        url, min_size=1, max_size=10, server_settings={"search_path": f"{schema},public"}, command_timeout=10
    )
    await migrate(pool, schema)
    return pool


def _scored(rows, reason: Reason) -> list[Scored]:
    return [Scored(r["product_id"], float(r["score"]), reason, r["category_id"]) for r in rows]


class PostgresStore:
    def __init__(self, pool: asyncpg.Pool):
        self.pool = pool

    async def ping(self) -> None:
        await self.pool.fetchval("SELECT 1")

    async def upsert_product(self, product_id: str, name: str, category_id: str, active: bool) -> None:
        await self.pool.execute(
            """INSERT INTO products (product_id, name, category_id, active, updated_at) VALUES ($1,$2,$3,$4,now())
               ON CONFLICT (product_id) DO UPDATE SET name=EXCLUDED.name, category_id=EXCLUDED.category_id,
                 active=EXCLUDED.active, updated_at=now()""",
            product_id,
            name,
            category_id,
            active,
        )

    async def delete_product(self, product_id: str) -> None:
        async with self.pool.acquire() as c, c.transaction():
            await c.execute("UPDATE products SET active = FALSE, updated_at = now() WHERE product_id = $1", product_id)
            await c.execute("DELETE FROM product_pairs WHERE product_a = $1 OR product_b = $1", product_id)

    async def product(self, product_id: str) -> tuple[str, str, bool] | None:
        r = await self.pool.fetchrow("SELECT name, category_id, active FROM products WHERE product_id = $1", product_id)
        return (r["name"], r["category_id"], r["active"]) if r else None

    async def record(self, subject: str, product_id: str, kind: Kind, weight: float, at: datetime) -> None:
        await self.pool.execute(
            "INSERT INTO interactions (subject, product_id, kind, weight, at) VALUES ($1,$2,$3,$4,$5)",
            subject,
            product_id,
            kind.value,
            weight,
            at,
        )

    async def record_order(self, customer_id: str, product_ids: list[str], at: datetime) -> None:
        ids = product_ids[:MAX_ITEMS_PER_ORDER]
        async with self.pool.acquire() as c, c.transaction():
            await c.executemany(
                "INSERT INTO interactions (subject, product_id, kind, weight, at) VALUES ($1,$2,'PURCHASE',5,$3)",
                [(customer_id, p, at) for p in ids],
            )
            await c.executemany(
                """INSERT INTO product_orders (product_id, orders) VALUES ($1, 1)
                   ON CONFLICT (product_id) DO UPDATE SET orders = product_orders.orders + 1""",
                [(p,) for p in ids],
            )
            pairs = [(a, b) for a, b in combinations(ids, 2)]
            await c.executemany(
                """INSERT INTO product_pairs (product_a, product_b, count, last_at) VALUES ($1,$2,1,$3)
                   ON CONFLICT (product_a, product_b) DO UPDATE SET count = product_pairs.count + 1, last_at = EXCLUDED.last_at""",
                [(a, b, at) for a, b in pairs] + [(b, a, at) for a, b in pairs],
            )

    _PAIRS = """SELECT pp.product_b AS product_id, p.category_id,
                  max(pp.count / sqrt(fa.orders::float8 * fb.orders::float8)) AS score
                FROM product_pairs pp
                JOIN product_orders fa ON fa.product_id = pp.product_a
                JOIN product_orders fb ON fb.product_id = pp.product_b
                JOIN products p ON p.product_id = pp.product_b AND p.active
                WHERE pp.product_a = ANY($1) AND pp.count >= $3 AND NOT pp.product_b = ANY($1)
                GROUP BY pp.product_b, p.category_id
                ORDER BY score DESC, pp.product_b LIMIT $2"""

    async def bought_together(self, product_id: str, limit: int, min_support: int) -> list[Scored]:
        rows = await self.pool.fetch(self._PAIRS, [product_id], limit, min_support)
        return _scored(rows, Reason.BOUGHT_TOGETHER)

    async def bought_with_any(self, product_ids: list[str], limit: int, min_support: int) -> list[Scored]:
        if not product_ids:
            return []
        rows = await self.pool.fetch(self._PAIRS, product_ids, limit, min_support)
        return _scored(rows, Reason.BOUGHT_TOGETHER)

    # Cửa sổ realtime cũ hơn mức này coi như đã hết hạn (job trượt 5 phút/lần, chừa trễ).
    STREAM_FRESH = timedelta(minutes=15)

    async def trending(self, limit: int, category_id: str | None, now: datetime) -> list[Scored]:
        """Trending realtime (Flink, 1 giờ gần nhất) đứng trước; phần còn thiếu lấy từ tương tác 14 ngày có giảm dần."""
        live = await self.pool.fetch(
            """SELECT t.product_id, p.category_id, t.score
               FROM stream_trending t JOIN products p ON p.product_id = t.product_id AND p.active
               WHERE t.window_end > $1::timestamptz - $4::interval AND t.window_end <= $1::timestamptz + interval '5 minutes'
                 AND ($3::text IS NULL OR p.category_id = $3)
               ORDER BY t.score DESC, t.product_id LIMIT $2""",
            now,
            limit,
            category_id,
            self.STREAM_FRESH,
        )
        out = _scored(live, Reason.TRENDING)
        if len(out) >= limit:
            return out
        rows = await self.pool.fetch(
            """SELECT i.product_id, p.category_id,
                 sum(i.weight * power(0.5, extract(epoch FROM ($1::timestamptz - i.at)) / 3600.0 / 72.0)) AS score
               FROM interactions i JOIN products p ON p.product_id = i.product_id AND p.active
               WHERE i.at > $1::timestamptz - interval '14 days' AND i.at <= $1::timestamptz
                 AND ($3::text IS NULL OR p.category_id = $3)
               GROUP BY i.product_id, p.category_id ORDER BY score DESC, i.product_id LIMIT $2""",
            now,
            limit,
            category_id,
        )
        seen = {s.product_id for s in out}
        out += [s for s in _scored(rows, Reason.TRENDING) if s.product_id not in seen]
        return out[:limit]

    async def upsert_stream_trending(self, product_id: str, score: float, window_end: datetime) -> None:
        """Chỉ nhận cửa sổ mới hơn (bản ghi Kafka đến trễ/lặp không ghi đè cửa sổ mới)."""
        await self.pool.execute(
            """INSERT INTO stream_trending (product_id, score, window_end) VALUES ($1, $2, $3)
               ON CONFLICT (product_id) DO UPDATE SET score = EXCLUDED.score, window_end = EXCLUDED.window_end, updated_at = now()
               WHERE stream_trending.window_end <= EXCLUDED.window_end""",
            product_id,
            score,
            window_end,
        )

    async def recent_interactions(self, subject: str, since: datetime, limit: int) -> list[tuple[str, Kind, float]]:
        """Mỗi sản phẩm một dòng: loại tương tác mạnh nhất, trọng số cộng dồn có giảm dần theo thời gian."""
        rows = await self.pool.fetch(
            """SELECT product_id,
                 (array_agg(kind ORDER BY weight DESC, at DESC))[1] AS kind,
                 sum(weight * power(0.5, extract(epoch FROM (now() - at)) / 3600.0 / 168.0)) AS w,
                 max(at) AS last_at
               FROM interactions WHERE subject = $1 AND at >= $2
               GROUP BY product_id ORDER BY last_at DESC LIMIT $3""",
            subject,
            since,
            limit,
        )
        return [(r["product_id"], Kind(r["kind"]), float(r["w"])) for r in rows]

    async def products_to_embed(self, limit: int) -> list[tuple[str, str, str]]:
        rows = await self.pool.fetch(
            """SELECT product_id, name, category_id FROM products
               WHERE active AND (embedded_at IS NULL OR embedded_at < updated_at) ORDER BY updated_at LIMIT $1""",
            limit,
        )
        return [(r["product_id"], r["name"], r["category_id"]) for r in rows]

    async def mark_embedded(self, product_ids: list[str], at: datetime) -> None:
        await self.pool.execute("UPDATE products SET embedded_at = $2 WHERE product_id = ANY($1)", product_ids, at)

    async def prune(self, before: datetime) -> int:
        r = await self.pool.execute("DELETE FROM interactions WHERE at < $1 AND kind <> 'PURCHASE'", before)
        await self.pool.execute("DELETE FROM product_pairs WHERE count = 1 AND last_at < $1", before)
        return int(r.split()[-1])

    async def once(self, c: asyncpg.Connection, event_id: str) -> bool:
        r = await c.execute(
            "INSERT INTO processed_events (event_id, consumer) VALUES ($1,$2) ON CONFLICT DO NOTHING",
            event_id,
            CONSUMER,
        )
        return r != "INSERT 0 0"


def _vec(v: list[float]) -> str:
    return "[" + ",".join(f"{x:.6g}" for x in v) + "]"


def _parse(text: str) -> list[float]:
    return [float(x) for x in text.strip("[]").split(",") if x]


class PgVectorIndex:
    """Bảng embeddings (pgvector, HNSW cosine). Vector là dữ liệu dẫn xuất: đổi số chiều thì tạo lại và nhúng lại."""

    enabled = True

    def __init__(self, pool: asyncpg.Pool, dim: int):
        self.pool = pool
        self.dim = dim

    async def ensure(self) -> bool:
        """Tạo bảng; trả về True nếu bảng vừa được tạo lại (cần nhúng lại toàn bộ)."""
        async with self.pool.acquire() as c, c.transaction():
            await c.execute("CREATE EXTENSION IF NOT EXISTS vector SCHEMA public")
            current = await c.fetchval(
                """SELECT a.atttypmod FROM pg_attribute a JOIN pg_class t ON t.oid = a.attrelid
                   JOIN pg_namespace n ON n.oid = t.relnamespace
                   WHERE t.relname = 'embeddings' AND n.nspname = current_schema() AND a.attname = 'embedding'"""
            )
            if current == self.dim:
                return False
            await c.execute("DROP TABLE IF EXISTS embeddings")
            await c.execute(
                f"""CREATE TABLE embeddings (
                      product_id  TEXT PRIMARY KEY,
                      category_id TEXT NOT NULL DEFAULT '',
                      embedding   vector({self.dim}) NOT NULL,
                      updated_at  TIMESTAMPTZ NOT NULL DEFAULT now())"""
            )
            await c.execute("CREATE INDEX embeddings_hnsw ON embeddings USING hnsw (embedding vector_cosine_ops)")
            await c.execute("UPDATE products SET embedded_at = NULL")
            return True

    async def upsert(self, items: list[tuple[str, str, list[float]]]) -> None:
        await self.pool.executemany(
            """INSERT INTO embeddings (product_id, category_id, embedding, updated_at) VALUES ($1,$2,$3::vector,now())
               ON CONFLICT (product_id) DO UPDATE SET category_id=EXCLUDED.category_id, embedding=EXCLUDED.embedding, updated_at=now()""",
            [(pid, cat, _vec(v)) for pid, cat, v in items],
        )

    async def delete(self, product_id: str) -> None:
        await self.pool.execute("DELETE FROM embeddings WHERE product_id = $1", product_id)

    async def similar(self, product_id: str, limit: int) -> list[Scored]:
        rows = await self.pool.fetch(
            """SELECT e.product_id, e.category_id, 1 - (e.embedding <=> q.embedding) AS score
               FROM embeddings q, embeddings e JOIN products p ON p.product_id = e.product_id AND p.active
               WHERE q.product_id = $1 AND e.product_id <> $1
               ORDER BY e.embedding <=> q.embedding LIMIT $2""",
            product_id,
            limit,
        )
        return _scored(rows, Reason.SIMILAR)

    async def vectors(self, product_ids: list[str]) -> dict[str, list[float]]:
        rows = await self.pool.fetch(
            "SELECT product_id, embedding::text AS v FROM embeddings WHERE product_id = ANY($1)", product_ids
        )
        return {r["product_id"]: _parse(r["v"]) for r in rows}

    async def nearest(self, vector: list[float], limit: int, exclude: list[str]) -> list[Scored]:
        rows = await self.pool.fetch(
            """SELECT e.product_id, e.category_id, 1 - (e.embedding <=> $1::vector) AS score
               FROM embeddings e JOIN products p ON p.product_id = e.product_id AND p.active
               WHERE NOT e.product_id = ANY($3)
               ORDER BY e.embedding <=> $1::vector LIMIT $2""",
            _vec(vector),
            limit,
            exclude,
        )
        return _scored(rows, Reason.FOR_YOU)


class NoVectorIndex:
    """VECTOR_ENABLED=false (Postgres không có pgvector): "tương tự" rơi về mua kèm + xu hướng cùng danh mục."""

    enabled = False

    async def upsert(self, items) -> None:
        return None

    async def delete(self, product_id: str) -> None:
        return None

    async def similar(self, product_id: str, limit: int) -> list[Scored]:
        return []

    async def vectors(self, product_ids: list[str]) -> dict[str, list[float]]:
        return {}

    async def nearest(self, vector, limit, exclude) -> list[Scored]:
        return []
