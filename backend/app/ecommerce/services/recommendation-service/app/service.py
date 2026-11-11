"""Use case gợi ý sản phẩm: mua kèm (co-purchase), tương tự (vector), dành cho bạn (pha trộn), xu hướng."""

from __future__ import annotations

import logging
from datetime import UTC, datetime, timedelta

from .domain.embedding import weighted_mean
from .domain.ranking import WEIGHTS, Kind, Reason, Scored, blend, decay
from .ports import Embedder, Store, VectorIndex

log = logging.getLogger(__name__)

MAX_LIMIT = 50


class Invalid(Exception):
    pass


class RecommendationService:
    def __init__(
        self,
        store: Store,
        index: VectorIndex,
        embedder: Embedder,
        min_support: int = 1,
        clock=None,
    ):
        self.store = store
        self.index = index
        self.embedder = embedder
        self.min_support = min_support
        self.now = clock or (lambda: datetime.now(UTC))

    @staticmethod
    def _limit(limit: int) -> int:
        return max(1, min(limit, MAX_LIMIT))

    # ---------- đọc ----------

    async def bought_together(self, product_id: str, limit: int = 10) -> list[Scored]:
        return await self.store.bought_together(product_id, self._limit(limit), self.min_support)

    async def similar(self, product_id: str, limit: int = 10) -> list[Scored]:
        limit = self._limit(limit)
        if self.index.enabled:
            found = await self.index.similar(product_id, limit)
            if found:
                return found
        # Chưa có vector (sản phẩm mới, hoặc tắt pgvector): dùng mua kèm, rồi xu hướng cùng danh mục.
        out = await self.store.bought_together(product_id, limit, self.min_support)
        if len(out) < limit:
            p = await self.store.product(product_id)
            cat = p[1] if p else None
            seen = {s.product_id for s in out} | {product_id}
            for s in await self.store.trending(limit * 2, cat or None, self.now()):
                if s.product_id not in seen and len(out) < limit:
                    out.append(Scored(s.product_id, s.score, Reason.SIMILAR, s.category_id))
        return out

    async def trending(self, limit: int = 20, category_id: str | None = None) -> list[Scored]:
        return await self.store.trending(self._limit(limit), category_id or None, self.now())

    async def for_you(self, subject: str | None, limit: int = 20) -> list[Scored]:
        """Pha trộn: mua kèm của sản phẩm đã quan tâm (0.5), gần vector sở thích (0.35), xu hướng (0.15)."""
        limit = self._limit(limit)
        now = self.now()
        trending = await self.store.trending(limit * 3, None, now)
        if not subject:
            return trending[:limit]
        history = await self.store.recent_interactions(subject, now - timedelta(days=90), 50)
        if not history:
            return trending[:limit]
        interacted = [pid for pid, _, _ in history]
        strong = [pid for pid, kind, _ in history if kind != Kind.VIEW][:20] or interacted[:20]
        co = await self.store.bought_with_any(strong, limit * 3, self.min_support)
        near: list[Scored] = []
        if self.index.enabled:
            vecs = await self.index.vectors(interacted)
            profile = weighted_mean([(vecs[pid], w) for pid, _, w in history if pid in vecs])
            if profile is not None:
                near = await self.index.nearest(profile, limit * 3, interacted)
        sources = [
            ([Scored(s.product_id, s.score, Reason.FOR_YOU, s.category_id) for s in co], 0.5),
            ([Scored(s.product_id, s.score, Reason.FOR_YOU, s.category_id) for s in near], 0.35),
            (trending, 0.15),
        ]
        return blend(sources, exclude=set(interacted), limit=limit)

    # ---------- ghi ----------

    async def record_event(self, subject: str, product_id: str, kind: Kind) -> None:
        if not subject or not product_id or len(product_id) > 64:
            raise Invalid("subject and productId are required")
        if kind == Kind.PURCHASE:
            raise Invalid("purchases come from order events")
        await self.store.record(subject, product_id, kind, WEIGHTS[kind], self.now())

    async def on_product_upserted(self, product_id: str, name: str, category_id: str, active: bool) -> None:
        await self.store.upsert_product(product_id, name, category_id, active)
        if not active:
            await self.index.delete(product_id)
            return
        if self.index.enabled:
            [vec] = await self.embedder.embed([(name, category_id)])
            await self.index.upsert([(product_id, category_id, vec)])
            await self.store.mark_embedded([product_id], self.now())

    async def on_product_deleted(self, product_id: str) -> None:
        await self.store.delete_product(product_id)
        await self.index.delete(product_id)

    async def on_stream_trending(self, product_id: str, score: float, window_end: datetime) -> None:
        if product_id:
            await self.store.upsert_stream_trending(product_id, max(0.0, score), window_end)

    async def on_order_placed(self, customer_id: str, product_ids: list[str]) -> None:
        await self.store.record_order(customer_id, sorted(set(product_ids)), self.now())

    async def rebuild(self, batch: int = 500) -> int:
        """Batch định kỳ: nhúng sản phẩm chưa có/đã đổi vector, dọn tương tác cũ (> 180 ngày)."""
        done = 0
        if self.index.enabled:
            while True:
                todo = await self.store.products_to_embed(batch)
                if not todo:
                    break
                vecs = await self.embedder.embed([(name, cat) for _, name, cat in todo])
                await self.index.upsert([(pid, cat, v) for (pid, _, cat), v in zip(todo, vecs, strict=True)])
                await self.store.mark_embedded([pid for pid, _, _ in todo], self.now())
                done += len(todo)
                if len(todo) < batch:
                    break
        pruned = await self.store.prune(self.now() - timedelta(days=180))
        log.info("recommendation rebuild done", extra={"x_embedded": done, "x_pruned": pruned})
        return done


__all__ = ["Invalid", "RecommendationService", "decay"]
