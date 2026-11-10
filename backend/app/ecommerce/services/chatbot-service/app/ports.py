"""Port (cổng ra) của chatbot; adapter cụ thể nằm trong app/adapters."""

from collections.abc import AsyncIterator
from typing import Protocol

from .domain.chat import Message, Product


class LLMError(Exception):
    """Model không trả lời được (quá tải, mất kết nối, từ chối nội dung)."""


class LLM(Protocol):
    def stream(self, system: str, messages: list[Message]) -> AsyncIterator[str]:
        """Phát lần lượt từng đoạn văn bản trả lời; lỗi ném LLMError."""
        ...


class ProductSearch(Protocol):
    async def search(self, query: str, limit: int) -> list[Product]: ...


class History(Protocol):
    async def load(self, key: str, turns: int) -> list[Message]: ...
    async def append(self, key: str, messages: list[Message], keep_turns: int, ttl: int) -> None: ...
    async def clear(self, key: str) -> None: ...
