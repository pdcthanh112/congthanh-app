"""fraud-service: chấm điểm rủi ro giao dịch lúc checkout (rule engine + velocity trên Redis),
hàng chờ duyệt thủ công và danh sách chặn; dựng lịch sử khách từ event đơn hàng/thanh toán/vận chuyển.

- POST /internal/fraud/assess: chỉ gọi nội bộ từ checkout-service (không định tuyến qua gateway).
- /api/ecommerce/fraud/*: backoffice (ADMIN hoặc FRAUD_ANALYST).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Annotated

from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import Principal, verifier_from_env
from ecommerce_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Depends, FastAPI, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from .config import settings
from .domain.rules import Address, Line, PaymentMethod, Thresholds, Transaction
from .service import Conflict, FraudService, Invalid, NotFound

eplog.setup("fraud-service")
log = logging.getLogger(__name__)
auth = Auth(verifier_from_env())
analyst = auth.roles("ADMIN", "FRAUD_ANALYST")


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .adapters import kafka, postgres
    from .adapters.redis_store import RedisStreamFeatures, RedisVelocity

    pool = await postgres.connect(settings.database_url, settings.db_schema)
    store = postgres.PostgresStore(pool)
    velocity = RedisVelocity(settings.redis_url)
    stream = RedisStreamFeatures(velocity.r)
    app.state.service = FraudService(
        store, velocity, Thresholds(review_at=settings.review_at, reject_at=settings.reject_at), stream=stream
    )
    app.state.ready = store.ping
    tasks: list[asyncio.Task] = []
    if settings.brokers:
        tasks.append(asyncio.create_task(kafka.run(store, settings.brokers, settings.kafka_group)))
        tasks.append(
            asyncio.create_task(kafka.run_stream_features(stream, settings.brokers, settings.kafka_group + "-stream"))
        )
    else:
        log.warning("KAFKA_BROKERS not set: customer history and stream features will stay empty")
    yield
    for t in tasks:
        t.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await t
    await velocity.close()
    await pool.close()


app = FastAPI(
    title="fraud-service",
    lifespan=lifespan,
    docs_url="/api/ecommerce/fraud/docs",
    openapi_url="/api/ecommerce/fraud/openapi.json",
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.ready()))


def service() -> FraudService:
    return app.state.service


Svc = Annotated[FraudService, Depends(service)]


class Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class AddressBody(Camel):
    country_code: str = Field(min_length=2, max_length=2)
    region_code: str = ""
    line1: str = ""
    phone: str = ""


class LineBody(Camel):
    product_id: str
    quantity: int = Field(ge=1)
    unit_price: int = Field(ge=0)


class AssessBody(Camel):
    checkout_id: str = Field(min_length=1)
    customer_id: str = Field(min_length=1)
    amount: int = Field(ge=0, description="đơn vị nhỏ nhất của tiền tệ")
    currency: str = Field(min_length=3, max_length=3)
    payment_method: PaymentMethod
    email: str = ""
    phone: str = ""
    ip: str = ""
    device_id: str = ""
    card_fingerprint: str = ""
    card_country: str = ""
    shipping_address: AddressBody | None = None
    lines: list[LineBody] = []
    account_created_at: datetime | None = None


@app.post("/internal/fraud/assess")
async def assess(body: AssessBody, svc: Svc):
    tx = Transaction(
        checkout_id=body.checkout_id,
        customer_id=body.customer_id,
        amount=body.amount,
        currency=body.currency.upper(),
        payment_method=body.payment_method,
        email=body.email,
        phone=body.phone,
        ip=body.ip,
        device_id=body.device_id,
        card_fingerprint=body.card_fingerprint,
        card_country=body.card_country,
        shipping=Address(**body.shipping_address.model_dump()) if body.shipping_address else None,
        lines=tuple(Line(**line.model_dump()) for line in body.lines),
        account_created_at=body.account_created_at,
    )
    aid, a = await svc.assess(tx)
    return ok(
        {
            "assessmentId": aid,
            "decision": a.decision.value,
            "score": a.score,
            "reasons": [{"code": h.code, "points": h.points, "detail": h.detail} for h in a.hits],
        },
        "Assess transaction successfully",
    )


def _errors(e: Exception) -> HTTPException:
    match e:
        case NotFound():
            return HTTPException(404, str(e))
        case Conflict():
            return HTTPException(409, str(e))
        case Invalid():
            return HTTPException(400, str(e))
    raise e


@app.get("/api/ecommerce/fraud/assessments/{key}")
async def get_assessment(key: str, svc: Svc, _: Annotated[Principal, Depends(analyst)]):
    try:
        return ok((await svc.get(key)).to_json(), "Get assessment successfully")
    except NotFound as e:
        raise _errors(e) from e


@app.get("/api/ecommerce/fraud/reviews")
async def list_reviews(
    svc: Svc,
    _: Annotated[Principal, Depends(analyst)],
    status: str = "PENDING",
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
):
    try:
        items = await svc.reviews(status, limit, offset)
    except Invalid as e:
        raise _errors(e) from e
    return ok([a.to_json() for a in items], "Get reviews successfully")


class DecisionBody(BaseModel):
    decision: str
    note: str = ""


@app.post("/api/ecommerce/fraud/reviews/{assessment_id}/decision")
async def decide(assessment_id: str, body: DecisionBody, svc: Svc, p: Annotated[Principal, Depends(analyst)]):
    try:
        a = await svc.decide(assessment_id, body.decision, p.subject or "", body.note)
    except (NotFound, Conflict, Invalid) as e:
        raise _errors(e) from e
    return ok(a.to_json(), "Decide review successfully")


class BlockBody(BaseModel):
    kind: str
    value: str
    reason: str = ""


@app.get("/api/ecommerce/fraud/blocklist")
async def blocklist(svc: Svc, _: Annotated[Principal, Depends(analyst)], limit: int = 100):
    return ok(await svc.blocks(limit), "Get blocklist successfully")


@app.post("/api/ecommerce/fraud/blocklist")
async def block(body: BlockBody, svc: Svc, p: Annotated[Principal, Depends(analyst)]):
    try:
        await svc.block(body.kind, body.value, body.reason, p.subject or "")
    except Invalid as e:
        raise _errors(e) from e
    return ok(None, "Add to blocklist successfully", 201)


@app.delete("/api/ecommerce/fraud/blocklist/{kind}/{value}")
async def unblock(kind: str, value: str, svc: Svc, _: Annotated[Principal, Depends(analyst)]):
    try:
        await svc.unblock(kind, value)
    except NotFound as e:
        raise _errors(e) from e
    return ok(None, "Remove from blocklist successfully")
