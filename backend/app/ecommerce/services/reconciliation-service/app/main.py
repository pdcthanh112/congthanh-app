"""reconciliation-service: đối soát giao dịch cổng thanh toán với ghi nhận nội bộ (ADR 0025).

- Nguồn cổng: Stripe Balance Transactions API (tự chạy hằng ngày cho ngày hôm trước) hoặc file CSV tải lên.
- Nguồn nội bộ: event PaymentCaptured/PaymentRefunded của payment-service.
- /api/ecommerce/reconciliation/*: team tài chính (FINANCE hoặc ADMIN).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager
from datetime import UTC, date, datetime, timedelta
from typing import Annotated

from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import Principal, verifier_from_env
from ecommerce_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Body, Depends, FastAPI, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from .config import settings
from .service import Invalid, NotFound, ReconciliationService

eplog.setup("reconciliation-service")
log = logging.getLogger(__name__)
auth = Auth(verifier_from_env())
finance = auth.roles("ADMIN", "FINANCE")
BASE = "/api/ecommerce/reconciliation"


async def daily(svc: ReconciliationService, hour: int) -> None:
    """Mỗi ngày lúc `hour` giờ UTC đối soát Stripe cho ngày hôm trước (chạy lại an toàn: lần mới thay lần cũ)."""
    while True:
        now = datetime.now(UTC)
        nxt = now.replace(hour=hour, minute=0, second=0, microsecond=0)
        if nxt <= now:
            nxt += timedelta(days=1)
        await asyncio.sleep((nxt - now).total_seconds())
        day = (datetime.now(UTC) - timedelta(days=1)).date()
        try:
            r = await svc.run_from_provider("stripe", day)
            log.info("daily stripe reconciliation", extra={"x_run": r})
        except Exception:
            log.exception("daily stripe reconciliation failed for %s", day)


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .adapters import kafka, postgres
    from .adapters.stripe import StripeReports

    pool = await postgres.connect(settings.database_url, settings.db_schema)
    store = postgres.PostgresStore(pool)
    reports = (
        {"stripe": StripeReports(settings.stripe_api_key, settings.stripe_api_base)} if settings.stripe_api_key else {}
    )
    svc = ReconciliationService(store, reports, settings.amount_tolerance_minor)
    app.state.service = svc
    app.state.ready = store.ping
    tasks: list[asyncio.Task] = []
    if settings.brokers:
        tasks.append(asyncio.create_task(kafka.run(store, settings.brokers, settings.kafka_group)))
    else:
        log.warning("KAFKA_BROKERS not set: internal ledger of payments stays empty")
    if reports and settings.daily_run_hour_utc >= 0:
        tasks.append(asyncio.create_task(daily(svc, settings.daily_run_hour_utc)))
    yield
    for t in tasks:
        t.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await t
    await pool.close()


app = FastAPI(
    title="reconciliation-service", lifespan=lifespan, docs_url=BASE + "/docs", openapi_url=BASE + "/openapi.json"
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.ready()))


def service() -> ReconciliationService:
    return app.state.service


Svc = Annotated[ReconciliationService, Depends(service)]
Finance = Annotated[Principal, Depends(finance)]


class Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RunBody(Camel):
    provider: str = Field(default="stripe", min_length=1)
    business_date: date


class ResolveBody(Camel):
    note: str = Field(min_length=1)


def _errors(e: Exception) -> HTTPException:
    if isinstance(e, NotFound):
        return HTTPException(404, str(e))
    return HTTPException(400, str(e))


@app.post(BASE + "/runs")
async def run(body: RunBody, svc: Svc, _: Finance):
    try:
        return ok(
            await svc.run_from_provider(body.provider.lower(), body.business_date), "Reconciliation run finished", 201
        )
    except (Invalid, NotFound) as e:
        raise _errors(e) from e


@app.post(BASE + "/runs/upload")
async def upload(
    svc: Svc,
    _: Finance,
    provider: Annotated[str, Query(min_length=1)],
    business_date: Annotated[date, Query(alias="businessDate")],
    csv: Annotated[str, Body(media_type="text/csv")],
):
    try:
        return ok(await svc.run_from_csv(provider.lower(), business_date, csv), "Reconciliation run finished", 201)
    except (Invalid, NotFound) as e:
        raise _errors(e) from e


@app.get(BASE + "/runs")
async def runs(svc: Svc, _: Finance, limit: int = 30):
    return ok(await svc.runs(limit), "List runs successfully")


@app.get(BASE + "/runs/{run_id}")
async def get_run(run_id: str, svc: Svc, _: Finance):
    try:
        return ok(await svc.run(run_id), "Get run successfully")
    except NotFound as e:
        raise _errors(e) from e


@app.get(BASE + "/mismatches")
async def mismatches(svc: Svc, _: Finance, status: str = "OPEN", limit: int = 100):
    try:
        return ok(await svc.mismatches(status.upper(), limit), "List mismatches successfully")
    except Invalid as e:
        raise _errors(e) from e


@app.post(BASE + "/mismatches/{mismatch_id}/resolve")
async def resolve(mismatch_id: int, body: ResolveBody, svc: Svc, p: Finance):
    try:
        return ok(await svc.resolve(mismatch_id, p.subject, body.note), "Mismatch resolved")
    except (Invalid, NotFound) as e:
        raise _errors(e) from e
