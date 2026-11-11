"""recommendation-service: gợi ý sản phẩm.

- GET  /api/ecommerce/recommendations/products/{id}/bought-together  mua kèm (co-purchase, chuẩn hoá cosine)
- GET  /api/ecommerce/recommendations/products/{id}/similar          tương tự (pgvector, HNSW cosine)
- GET  /api/ecommerce/recommendations/for-you                         dành cho bạn (pha trộn, đa dạng danh mục)
- GET  /api/ecommerce/recommendations/trending                        xu hướng (tương tác giảm dần theo thời gian)
- POST /api/ecommerce/recommendations/events                          storefront ghi lượt xem / thêm giỏ
Tín hiệu mua hàng, giỏ hàng, wishlist và danh mục sản phẩm đến từ Kafka.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
import re
from contextlib import asynccontextmanager
from typing import Annotated

from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import Principal, verifier_from_env
from ecommerce_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

from .config import settings
from .domain.embedding import HashingEmbedder
from .domain.ranking import Kind, Scored
from .service import Invalid, RecommendationService

eplog.setup("recommendation-service")
log = logging.getLogger(__name__)
auth = Auth(verifier_from_env())
admin = auth.roles("ADMIN")
SESSION_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


async def _rebuild_loop(svc: RecommendationService, every: int) -> None:
    while True:
        try:
            await svc.rebuild()
        except Exception:
            log.exception("recommendation rebuild failed")
        await asyncio.sleep(every)


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .adapters import kafka, postgres
    from .adapters.ollama import OllamaEmbedder

    pool = await postgres.connect(settings.database_url, settings.db_schema)
    store = postgres.PostgresStore(pool)
    embedder = (
        OllamaEmbedder(settings.ollama_url, settings.embedding_model, settings.embedding_dim)
        if settings.embeddings_provider == "ollama"
        else HashingEmbedder(settings.embedding_dim)
    )
    if settings.vector_enabled:
        index = postgres.PgVectorIndex(pool, settings.embedding_dim)
        if await index.ensure():
            log.info("embeddings table (re)created", extra={"x_dim": settings.embedding_dim})
    else:
        index = postgres.NoVectorIndex()
    svc = RecommendationService(store, index, embedder, settings.min_support)
    app.state.service = svc
    app.state.ready = store.ping
    tasks = [asyncio.create_task(_rebuild_loop(svc, settings.rebuild_interval_seconds))]
    if settings.brokers:
        tasks.append(asyncio.create_task(kafka.run(svc, store, settings.brokers, settings.kafka_group)))
    else:
        log.warning("KAFKA_BROKERS not set: recommendations only learn from the events API")
    yield
    for t in tasks:
        t.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await t
    if isinstance(embedder, OllamaEmbedder):
        await embedder.close()
    await pool.close()


app = FastAPI(
    title="recommendation-service",
    lifespan=lifespan,
    docs_url="/api/ecommerce/recommendations/docs",
    openapi_url="/api/ecommerce/recommendations/openapi.json",
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.ready()))


def service() -> RecommendationService:
    return app.state.service


Svc = Annotated[RecommendationService, Depends(service)]
Limit = Annotated[int, Query(ge=1, le=50)]


def _items(items: list[Scored]) -> list[dict]:
    return [{"productId": s.product_id, "score": round(s.score, 4), "reason": s.reason.value} for s in items]


def subject(p: Principal | None, session_id: str | None) -> str | None:
    """Khách đăng nhập: customer id; chưa đăng nhập: "s:" + X-Session-Id do storefront sinh."""
    if p is not None and p.subject:
        return p.subject
    if session_id and SESSION_RE.match(session_id):
        return "s:" + session_id
    return None


@app.get("/api/ecommerce/recommendations/products/{product_id}/bought-together")
async def bought_together(product_id: str, svc: Svc, limit: Limit = 10):
    return ok(_items(await svc.bought_together(product_id, limit)), "Get bought-together products successfully")


@app.get("/api/ecommerce/recommendations/products/{product_id}/similar")
async def similar(product_id: str, svc: Svc, limit: Limit = 10):
    return ok(_items(await svc.similar(product_id, limit)), "Get similar products successfully")


@app.get("/api/ecommerce/recommendations/trending")
async def trending(svc: Svc, limit: Limit = 20, category_id: Annotated[str | None, Query(alias="categoryId")] = None):
    return ok(_items(await svc.trending(limit, category_id)), "Get trending products successfully")


@app.get("/api/ecommerce/recommendations/for-you")
async def for_you(
    svc: Svc,
    p: Annotated[Principal | None, Depends(auth.optional)],
    limit: Limit = 20,
    x_session_id: Annotated[str | None, Header()] = None,
):
    return ok(_items(await svc.for_you(subject(p, x_session_id), limit)), "Get recommendations successfully")


class EventBody(BaseModel):
    type: Kind
    product_id: str = Field(alias="productId", min_length=1, max_length=64)


@app.post("/api/ecommerce/recommendations/events", status_code=202)
async def record_event(
    body: EventBody,
    svc: Svc,
    p: Annotated[Principal | None, Depends(auth.optional)],
    x_session_id: Annotated[str | None, Header()] = None,
):
    if body.type not in (Kind.VIEW, Kind.CART):
        raise HTTPException(400, "type must be VIEW or CART")
    who = subject(p, x_session_id)
    if who is None:
        raise HTTPException(400, "sign in or send an X-Session-Id header")
    try:
        await svc.record_event(who, body.product_id, body.type)
    except Invalid as e:
        raise HTTPException(400, str(e)) from e
    return ok(None, "Event recorded", 202)


@app.post("/api/ecommerce/recommendations/rebuild", status_code=202)
async def rebuild(svc: Svc, _: Annotated[Principal, Depends(admin)]):
    embedded = await svc.rebuild()
    return ok({"embedded": embedded}, "Rebuild finished", 202)
