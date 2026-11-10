"""Quy tắc dịch: ngôn ngữ hỗ trợ, giới hạn đầu vào, khoá cache."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from enum import StrEnum

# BCP 47 → tên dùng trong prompt.
LANGUAGES: dict[str, str] = {
    "vi": "Vietnamese",
    "en": "English",
    "zh": "Simplified Chinese",
    "ja": "Japanese",
    "ko": "Korean",
    "th": "Thai",
    "fr": "French",
    "de": "German",
    "es": "Spanish",
}


class Format(StrEnum):
    TEXT = "text"
    HTML = "html"  # giữ nguyên thẻ HTML, chỉ dịch nội dung chữ


class InvalidRequest(ValueError):
    pass


@dataclass(frozen=True)
class TranslateRequest:
    texts: tuple[str, ...]
    target: str
    source: str | None = None
    format: Format = Format.TEXT

    def validate(self, max_texts: int, max_chars: int) -> None:
        if self.target not in LANGUAGES:
            raise InvalidRequest(f"unsupported target language {self.target!r}; supported: {', '.join(LANGUAGES)}")
        if self.source is not None and self.source not in LANGUAGES:
            raise InvalidRequest(f"unsupported source language {self.source!r}")
        if not self.texts:
            raise InvalidRequest("texts must not be empty")
        if len(self.texts) > max_texts:
            raise InvalidRequest(f"at most {max_texts} texts per request")
        if sum(len(t) for t in self.texts) > max_chars:
            raise InvalidRequest(f"total length must not exceed {max_chars} characters")


def cache_key(model: str, req: TranslateRequest, text: str) -> str:
    h = hashlib.sha256(f"{model}\x00{req.source or ''}\x00{req.target}\x00{req.format}\x00{text}".encode()).hexdigest()
    return f"translate:v1:{h}"
