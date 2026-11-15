"""Use case kiểm duyệt."""

from __future__ import annotations

import hashlib
from typing import Any

from .domain.policy import ContentType, Decision, Item, Verdict, evaluate, merge_llm
from .ports import Classifier, Store


class Invalid(Exception):
    pass


class NotFound(Exception):
    pass


class Forbidden(Exception):
    pass


MAX_TEXT = 20_000


def content_hash(content_type: ContentType, text: str) -> str:
    return hashlib.sha256(f"{content_type.value}\n{' '.join(text.split()).lower()}".encode()).hexdigest()


class ModerationService:
    def __init__(
        self,
        store: Store,
        classifier: Classifier | None,
        banned_terms: tuple[str, ...] = (),
        strike_threshold: int = 3,
        strike_window_days: int = 30,
    ):
        self.store = store
        self.classifier = classifier
        self.banned = banned_terms
        self.strike_threshold = strike_threshold
        self.strike_window = strike_window_days

    async def submit(self, content_type: str, content_id: str, author_id: str, text: str) -> dict[str, Any]:
        """Kiểm duyệt đồng bộ (luật, rồi LLM nếu không chắc). Nội dung giống hệt đã duyệt thì dùng lại quyết định."""
        try:
            ctype = ContentType(content_type.upper())
        except ValueError as e:
            raise Invalid(f"unknown content type {content_type!r}") from e
        if not content_id or not author_id:
            raise Invalid("contentId and authorId are required")
        if len(text) > MAX_TEXT:
            raise Invalid(f"text is longer than {MAX_TEXT} characters")
        item = Item(ctype, content_id, author_id, text)
        h = content_hash(ctype, text)
        cached = await self.store.cached_verdict(h)
        if cached is not None:
            verdict = Verdict(Decision(cached["decision"]), cached["categories"], cached["redacted_text"], "cached")
        else:
            verdict = evaluate(item, self.banned)
            if verdict.uncertain and self.classifier is not None and ctype != ContentType.MESSAGE:
                verdict = merge_llm(verdict, await self.classifier.classify(item))
        saved = await self.store.save_item(item, h, verdict, "auto")
        if verdict.decision == Decision.REJECT:
            await self._maybe_strike(author_id, saved["id"])
        return saved

    async def queue(self, limit: int) -> list[dict[str, Any]]:
        return await self.store.queue(min(max(limit, 1), 200))

    async def get(self, item_id: str) -> dict[str, Any]:
        r = await self.store.get(item_id)
        if r is None:
            raise NotFound(f"item {item_id} not found")
        return r

    async def decide(self, item_id: str, decision: str, by: str, note: str, redacted_text: str = "") -> dict[str, Any]:
        try:
            d = Decision(decision.upper())
        except ValueError as e:
            raise Invalid(f"unknown decision {decision!r}") from e
        if d == Decision.ESCALATE:
            raise Invalid("a reviewer must approve, reject or redact")
        if d == Decision.REDACT and not redacted_text.strip():
            raise Invalid("REDACT needs the redacted text")
        current = await self.get(item_id)
        if current["status"] == "APPEALED" and current.get("decidedBy") == by:
            raise Forbidden("an appeal must be reviewed by someone other than the original reviewer")
        r = await self.store.decide(item_id, Verdict(d, [], redacted_text, note), by, note)
        if r is None:
            raise Invalid(f"item {item_id} is not waiting for a decision")
        if d == Decision.REJECT:
            await self._maybe_strike(r["authorId"], item_id)
        return r

    async def appeal(self, item_id: str, author_id: str, reason: str) -> dict[str, Any]:
        if not reason.strip():
            raise Invalid("an appeal needs a reason")
        current = await self.get(item_id)
        if current["authorId"] != author_id:
            raise NotFound(f"item {item_id} not found")
        r = await self.store.appeal(item_id, author_id, reason)
        if r is None:
            raise Invalid("only rejected or redacted content can be appealed, once")
        return r

    async def _maybe_strike(self, author_id: str, item_id: str) -> None:
        n = await self.store.rejections_since(author_id, self.strike_window)
        if n >= self.strike_threshold:
            await self.store.issue_strike(author_id, n, item_id)
