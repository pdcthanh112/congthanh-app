"""Use case: một lượt chat. Tìm sản phẩm liên quan, ghép lịch sử, stream câu trả lời rồi lưu lịch sử."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass

from .domain.chat import Message, Product, history_key, system_prompt, validate
from .ports import LLM, History, LLMError, ProductSearch

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Event:
    """Sự kiện gửi về client qua SSE: products (một lần, đầu tiên), delta (nhiều lần), done | error."""

    type: str
    data: object


class ChatService:
    def __init__(
        self,
        llm: LLM,
        search: ProductSearch,
        history: History,
        *,
        store: str,
        context_size: int,
        turns: int,
        ttl: int,
        max_chars: int,
    ):
        self.llm, self.search, self.history = llm, search, history
        self.store, self.context_size, self.turns, self.ttl, self.max_chars = store, context_size, turns, ttl, max_chars

    async def _products(self, text: str) -> list[Product]:
        try:
            return await self.search.search(text, self.context_size)
        except Exception:
            # Thiếu ngữ cảnh sản phẩm vẫn trả lời được câu hỏi chung (đổi trả, vận chuyển...).
            log.warning("product search failed", exc_info=True)
            return []

    async def _load(self, key: str | None) -> list[Message]:
        if key is None:
            return []
        try:
            return await self.history.load(key, self.turns)
        except Exception:
            log.warning("history load failed", exc_info=True)
            return []

    def prepare(self, text: str, session_id: str | None) -> str:
        """Kiểm đầu vào trước khi mở stream (lỗi ở đây trả 400 thay vì sự kiện error)."""
        return validate(text, session_id, self.max_chars)

    async def chat(self, text: str, session_id: str | None, subject: str | None) -> AsyncIterator[Event]:
        key = history_key(subject, session_id) if session_id else None
        products = await self._products(text)
        yield Event("products", [p.__dict__ for p in products])

        messages = [*await self._load(key), Message("user", text)]
        parts: list[str] = []
        try:
            async for chunk in self.llm.stream(system_prompt(self.store, products), messages):
                parts.append(chunk)
                yield Event("delta", chunk)
        except LLMError as e:
            yield Event("error", str(e))
            return

        answer = "".join(parts)
        if key is not None and answer:
            try:
                await self.history.append(
                    key, [Message("user", text), Message("assistant", answer)], self.turns, self.ttl
                )
            except Exception:
                log.warning("history save failed", exc_info=True)
        yield Event("done", {"sessionId": session_id})

    async def reset(self, session_id: str, subject: str | None) -> None:
        validate("x", session_id, self.max_chars)
        await self.history.clear(history_key(subject, session_id))
