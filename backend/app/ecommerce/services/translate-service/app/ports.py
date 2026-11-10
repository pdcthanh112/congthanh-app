from __future__ import annotations

from typing import Protocol

from .domain.translation import TranslateRequest


class Translator(Protocol):
    """Driven port: dịch một lô văn bản, trả đúng số phần tử theo thứ tự."""

    model: str

    async def translate(self, req: TranslateRequest, texts: list[str]) -> list[str]: ...


class Cache(Protocol):
    async def get_many(self, keys: list[str]) -> list[str | None]: ...
    async def set_many(self, items: dict[str, str], ttl: int) -> None: ...
