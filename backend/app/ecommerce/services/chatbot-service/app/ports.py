"""Port của chatbot-service: model ngôn ngữ, cửa hàng (GraphQL storefront), lịch sử hội thoại."""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any, Protocol

from .domain.chat import Message, Turn


class LLMError(Exception):
    """Model không trả lời được (quá tải, mất kết nối, từ chối nội dung); message an toàn để hiện cho khách."""


class ShopError(Exception):
    """Lỗi nghiệp vụ từ cửa hàng (không tìm thấy, hết hàng, chưa đăng nhập...); message an toàn để đưa cho model."""


class ShopUnavailable(Exception):
    """Cửa hàng tạm không trả lời (mạng, timeout, lỗi 5xx)."""


@dataclass(frozen=True, slots=True)
class ToolCall:
    id: str
    name: str
    input: dict[str, Any]


@dataclass(frozen=True, slots=True)
class TextDelta:
    text: str


@dataclass(frozen=True, slots=True)
class TurnEnd:
    """Kết thúc một lần gọi model: content là các block để nối nguyên văn vào lịch sử."""

    content: list[dict[str, Any]]
    stop_reason: str
    tool_calls: list[ToolCall] = field(default_factory=list)


LLMEvent = TextDelta | TurnEnd


class LLM(Protocol):
    @property
    def supports_tools(self) -> bool:
        """False với model tự host chưa hỗ trợ tool: service tự tìm sản phẩm trước rồi đưa vào prompt."""
        ...

    def turn(self, system: str, messages: list[Message], tools: list[dict[str, Any]]) -> AsyncIterator[LLMEvent]:
        """Phát TextDelta trong lúc sinh, kết thúc bằng đúng một TurnEnd; lỗi ném LLMError."""
        ...


class Shop(Protocol):
    """Thao tác cửa hàng qua GraphQL storefront, chạy bằng token của chính khách (None = khách vãng lai)."""

    async def search(
        self, text: str, *, min_price: float | None, max_price: float | None, sort: str | None, limit: int
    ) -> list[dict[str, Any]]: ...

    async def product(self, product_id: str | None, slug: str | None) -> dict[str, Any] | None: ...

    async def recommendations(
        self, kind: str, product_id: str | None, limit: int, token: str | None, session_id: str | None
    ) -> list[dict[str, Any]]: ...

    async def cart(self, token: str) -> dict[str, Any]: ...

    async def add_to_cart(self, token: str, product_id: str, quantity: int) -> dict[str, Any]: ...

    async def update_cart_item(self, token: str, item_id: str, quantity: int) -> dict[str, Any]: ...

    async def orders(self, token: str, limit: int) -> list[dict[str, Any]]: ...

    async def order(self, token: str, order_code: str) -> dict[str, Any]: ...


class History(Protocol):
    async def load(self, key: str, turns: int) -> list[Turn]:
        """Tối đa `turns` lượt gần nhất, cũ trước mới sau."""
        ...

    async def append(self, key: str, turn: Turn, keep_turns: int, ttl: int) -> None: ...

    async def clear(self, key: str) -> None: ...
