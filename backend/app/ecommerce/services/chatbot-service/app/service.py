"""Use case của chatbot-service: vòng lặp agent (model gọi tool cửa hàng thay khách) và stream sự kiện cho widget."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

from .domain.chat import Message, Turn, history_key, system_prompt, validate, window
from .ports import LLM, History, LLMError, ShopUnavailable, TextDelta, ToolCall, TurnEnd
from .tools import ToolBox, ToolOutcome

log = logging.getLogger(__name__)

# Số lần model được gọi tool trong một lượt; hết lượt thì buộc trả lời bằng dữ liệu đã có.
MAX_TOOL_ROUNDS = 6
# Trần thao tác ghi (thêm/sửa giỏ) mỗi lượt, chặn vòng lặp ghi khi model hiểu sai hoặc bị dữ liệu độc dẫn dắt.
MAX_WRITES_PER_TURN = 5
# Số sản phẩm tìm trước cho model không hỗ trợ tool.
PREFETCH_PRODUCTS = 5
_FINAL_ROUND_NOTE = (
    "Tool budget for this turn is used up. Answer the customer now with the information you already have."
)


@dataclass(frozen=True, slots=True)
class Event:
    type: str
    data: object


@dataclass(frozen=True, slots=True)
class Customer:
    """Người đang chat: subject + access token khi đăng nhập (token chỉ dùng trong lượt này, không lưu)."""

    subject: str | None = None
    token: str | None = None

    @property
    def signed_in(self) -> bool:
        return bool(self.subject and self.token)


class ChatService:
    def __init__(
        self,
        llm: LLM,
        tools: ToolBox,
        history: History,
        *,
        store: str,
        turns: int,
        ttl: int,
        max_chars: int,
    ):
        self.llm, self.tools, self.history = llm, tools, history
        self.store, self.turns, self.ttl, self.max_chars = store, turns, ttl, max_chars

    def prepare(self, text: str, session_id: str | None) -> str:
        return validate(text, session_id, self.max_chars)

    async def chat(self, text: str, session_id: str | None, customer: Customer) -> AsyncIterator[Event]:
        """Một lượt chat. Phát: delta, tool_started/tool_finished, products, cart_updated, rồi done hoặc error.

        Chỉ lượt hoàn tất mới lưu vào lịch sử; lượt lỗi giữa chừng bỏ đi để lần sau không gửi lại tool_use dở dang.
        """
        key = history_key(customer.subject, session_id) if session_id else None
        system = system_prompt(self.store, customer.signed_in)
        tools = self.tools.definitions(customer.signed_in) if self.llm.supports_tools else []
        if not self.llm.supports_tools:
            products, catalogue = await self._prefetch(text, session_id)
            yield Event("products", products)
            system = f"{system}\n\nYou cannot call tools. Products matching the question:\n<catalogue>\n{catalogue}\n</catalogue>"

        history = await self._load(key)
        turn = [Message("user", text)]
        writes = 0
        for round_no in range(MAX_TOOL_ROUNDS + 1):
            if round_no == MAX_TOOL_ROUNDS:
                turn.append(Message("system", _FINAL_ROUND_NOTE))
            end: TurnEnd | None = None
            try:
                async for ev in self.llm.turn(system, history + turn, tools):
                    if isinstance(ev, TextDelta):
                        yield Event("delta", ev.text)
                    else:
                        end = ev
            except LLMError as e:
                yield Event("error", str(e))
                return
            if end is None:
                yield Event("error", "the assistant is unavailable right now")
                return
            turn.append(Message("assistant", end.content))
            if end.stop_reason == "refusal":
                yield Event("error", "the assistant can't help with that request")
                return
            if not end.tool_calls:
                break
            if end.stop_reason == "max_tokens":
                # Tham số tool bị cắt giữa chừng vẫn parse ra object hợp lệ: không chạy.
                yield Event("error", "the answer was too long, please ask a narrower question")
                return
            results: list[dict[str, Any]] = []
            async for e, result, wrote in self._run_tools(end.tool_calls, customer, session_id, writes):
                if e is not None:
                    yield e
                if result is not None:
                    results.append(result)
                writes += wrote
            turn.append(Message("user", results))

        if key is not None:
            await self._save(key, Turn(turn))
        yield Event("done", {"sessionId": session_id})

    async def reset(self, session_id: str, subject: str | None) -> None:
        validate("x", session_id, self.max_chars)
        await self.history.clear(history_key(subject, session_id))

    # ---------- tool ----------

    async def _run_tools(
        self, calls: list[ToolCall], customer: Customer, session_id: str | None, writes: int
    ) -> AsyncIterator[tuple[Event | None, dict[str, Any] | None, int]]:
        """Tool đọc chạy song song; tool ghi chạy tuần tự theo thứ tự model gọi. Mọi tool_result trả trong một message."""
        for call in calls:
            yield Event("tool_started", {"id": call.id, "name": call.name}), None, 0

        reads = [c for c in calls if not self.tools.writes(c.name)]
        outcomes: dict[str, ToolOutcome] = dict(
            zip(
                [c.id for c in reads],
                await asyncio.gather(*(self._run_one(c, customer, session_id) for c in reads)),
                strict=True,
            )
        )
        for call in calls:
            if not self.tools.writes(call.name):
                continue
            if writes >= MAX_WRITES_PER_TURN:
                outcomes[call.id] = ToolOutcome("too many cart changes in one turn; ask the customer", is_error=True)
                continue
            outcomes[call.id] = await self._run_one(call, customer, session_id)
            writes += 1

        for call in calls:
            o = outcomes[call.id]
            if o.products:
                yield Event("products", o.products), None, 0
            if o.cart is not None:
                yield Event("cart_updated", o.cart), None, 0
            wrote = 1 if self.tools.writes(call.name) and not o.is_error else 0
            yield (
                Event("tool_finished", {"id": call.id, "name": call.name, "ok": not o.is_error}),
                _result(call, o),
                wrote,
            )

    async def _run_one(self, call: ToolCall, customer: Customer, session_id: str | None) -> ToolOutcome:
        try:
            return await self.tools.run(call, customer.token, session_id)
        except ShopUnavailable:
            log.warning("shop unavailable for tool", extra={"x_tool": call.name}, exc_info=True)
            return ToolOutcome(
                "the shop is temporarily unavailable; tell the customer to try again shortly", is_error=True
            )

    # ---------- model không hỗ trợ tool ----------

    async def _prefetch(self, text: str, session_id: str | None) -> tuple[list[dict[str, Any]], str]:
        """Tìm sản phẩm trước thay cho tool: trả (thẻ sản phẩm cho widget, danh sách rút gọn cho prompt)."""
        call = ToolCall("prefetch", "search_products", {"query": text, "limit": PREFETCH_PRODUCTS})
        outcome = await self._run_one(call, Customer(), session_id)
        if outcome.is_error or not outcome.products:
            return [], "no matching products"
        return outcome.products, outcome.content

    # ---------- lịch sử ----------

    async def _load(self, key: str | None) -> list[Message]:
        if key is None:
            return []
        try:
            return window(await self.history.load(key, self.turns + 1), self.turns)
        except Exception:
            log.warning("history load failed", exc_info=True)
            return []

    async def _save(self, key: str, turn: Turn) -> None:
        try:
            await self.history.append(key, turn, self.turns + 1, self.ttl)
        except Exception:
            log.warning("history save failed", exc_info=True)


def _result(call: ToolCall, o: ToolOutcome) -> dict[str, Any]:
    block: dict[str, Any] = {"type": "tool_result", "tool_use_id": call.id, "content": o.content}
    if o.is_error:
        block["is_error"] = True
    return block
