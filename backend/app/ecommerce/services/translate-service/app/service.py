"""Use case: dịch theo lô, lấy từ cache trước, chỉ gửi phần chưa có cho model."""

from __future__ import annotations

import logging

from .domain.translation import TranslateRequest, cache_key
from .ports import Cache, Translator

log = logging.getLogger(__name__)


class TranslateService:
    def __init__(self, translator: Translator, cache: Cache, ttl: int, max_texts: int, max_chars: int):
        self.translator, self.cache, self.ttl = translator, cache, ttl
        self.max_texts, self.max_chars = max_texts, max_chars

    async def translate(self, req: TranslateRequest) -> list[str]:
        req.validate(self.max_texts, self.max_chars)
        out: list[str | None] = [t if not t.strip() else None for t in req.texts]
        keys = [cache_key(self.translator.model, req, t) for t in req.texts]
        try:
            cached = await self.cache.get_many(keys)
        except Exception as e:
            log.warning("translation cache unavailable: %s", e)
            cached = [None] * len(keys)
        for i, c in enumerate(cached):
            if out[i] is None and c is not None:
                out[i] = c

        # Văn bản trùng trong cùng request chỉ gửi một lần.
        missing = list(dict.fromkeys(req.texts[i] for i, v in enumerate(out) if v is None))
        if missing:
            translated = await self.translator.translate(req, missing)
            by_text = dict(zip(missing, translated, strict=True))
            for i, v in enumerate(out):
                if v is None:
                    out[i] = by_text[req.texts[i]]
            try:
                await self.cache.set_many(
                    {cache_key(self.translator.model, req, t): by_text[t] for t in missing}, self.ttl
                )
            except Exception as e:
                log.warning("could not store translations in cache: %s", e)
        return [v or "" for v in out]
