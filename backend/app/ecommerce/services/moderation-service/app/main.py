"""moderation-service: kiểm duyệt nội dung dùng chung (ADR 0026).

- POST /internal/moderation/items: service sở hữu nội dung (review, message, product, supplier) gửi nội dung lên
  ngay khi tạo; chỉ gọi nội bộ (không định tuyến qua gateway). Trả quyết định đồng bộ.
- /api/ecommerce/moderation/*: hàng chờ và quyết định của người duyệt (ADMIN, MODERATOR); khiếu nại của tác giả.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Annotated

from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import Principal, verifier_from_env
from ecommerce_platform.fastapi import Auth, health_router, install_error_handlers, ok
from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from .config import settings
from .service import Forbidden, Invalid, ModerationService, NotFound

eplog.setup("moderation-service")
log = logging.getLogger(__name__)
auth = Auth(verifier_from_env())
moderator = auth.roles("ADMIN", "MODERATOR")
BASE = "/api/ecommerce/moderation"


@asynccontextmanager
async def lifespan(app: FastAPI):
    import os

    from .adapters import postgres

    pool = await postgres.connect(settings.database_url, settings.db_schema)
    store = postgres.PostgresStore(pool, settings.policy_version)
    classifier = None
    if settings.llm_enabled and os.environ.get("ANTHROPIC_API_KEY"):
        import anthropic

        from .adapters.claude import ClaudeClassifier

        classifier = ClaudeClassifier(
            anthropic.AsyncAnthropic(), settings.anthropic_model, settings.llm_timeout_seconds
        )
    else:
        log.warning("LLM tier disabled: only rules run, uncertain content is approved")
    banned = tuple(t for t in settings.banned_terms.split(",") if t.strip())
    app.state.service = ModerationService(
        store, classifier, banned, settings.strike_threshold, settings.strike_window_days
    )
    app.state.ready = store.ping
    tasks = []
    if settings.brokers:
        import asyncio

        from .adapters import privacy

        tasks.append(asyncio.create_task(privacy.run(pool, settings.brokers, settings.kafka_group)))
    yield
    for t in tasks:
        t.cancel()
    await pool.close()


app = FastAPI(
    title="moderation-service", lifespan=lifespan, docs_url=BASE + "/docs", openapi_url=BASE + "/openapi.json"
)
install_error_handlers(app)
app.include_router(health_router(lambda: app.state.ready()))


def service() -> ModerationService:
    return app.state.service


Svc = Annotated[ModerationService, Depends(service)]
Moderator = Annotated[Principal, Depends(moderator)]


class Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SubmitBody(Camel):
    content_type: str
    content_id: str = Field(min_length=1)
    author_id: str = Field(min_length=1)
    text: str


class DecisionBody(Camel):
    decision: str
    note: str = Field(min_length=1)
    redacted_text: str = ""


class AppealBody(Camel):
    reason: str = Field(min_length=1)


def _http(e: Exception) -> HTTPException:
    if isinstance(e, NotFound):
        return HTTPException(404, str(e))
    if isinstance(e, Forbidden):
        return HTTPException(403, str(e))
    return HTTPException(400, str(e))


@app.post("/internal/moderation/items")
async def submit(body: SubmitBody, svc: Svc):
    try:
        return ok(await svc.submit(body.content_type, body.content_id, body.author_id, body.text), "Moderated")
    except Invalid as e:
        raise _http(e) from e


@app.get(BASE + "/queue")
async def queue(svc: Svc, _: Moderator, limit: int = 50):
    return ok(await svc.queue(limit), "Get queue successfully")


@app.get(BASE + "/items/{item_id}")
async def get_item(item_id: str, svc: Svc, _: Moderator):
    try:
        return ok(await svc.get(item_id), "Get item successfully")
    except NotFound as e:
        raise _http(e) from e


@app.post(BASE + "/items/{item_id}/decision")
async def decide(item_id: str, body: DecisionBody, svc: Svc, p: Moderator):
    try:
        return ok(
            await svc.decide(item_id, body.decision, p.subject, body.note, body.redacted_text), "Decision recorded"
        )
    except (Invalid, NotFound, Forbidden) as e:
        raise _http(e) from e


@app.post(BASE + "/items/{item_id}/appeal")
async def appeal(item_id: str, body: AppealBody, svc: Svc, p: Annotated[Principal, Depends(auth.required)]):
    try:
        return ok(await svc.appeal(item_id, p.subject, body.reason), "Appeal submitted")
    except (Invalid, NotFound) as e:
        raise _http(e) from e
