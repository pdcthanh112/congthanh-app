"""Embedding nội dung sản phẩm không cần model: feature hashing trên token tên + danh mục.

Chạy được ngay, đủ tốt cho "sản phẩm tương tự" theo từ khoá (cả tiếng Việt có dấu và không dấu).
Muốn tương đồng ngữ nghĩa đa ngôn ngữ thì bật EMBEDDINGS_PROVIDER=ollama (vd. model bge-m3).
"""

from __future__ import annotations

import hashlib
import itertools
import math
import re
import unicodedata

_TOKEN = re.compile(r"[\w]+", re.UNICODE)


def strip_accents(s: str) -> str:
    s = s.replace("đ", "d").replace("Đ", "D")
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def tokens(text: str) -> list[str]:
    words = [w for w in _TOKEN.findall(text.lower()) if len(w) > 1 or w.isdigit()]
    plain = [strip_accents(w) for w in words]
    out = list(words)
    out += [p for p, w in zip(plain, words, strict=True) if p != w]
    out += [f"{a}_{b}" for a, b in itertools.pairwise(plain)]
    return out


def product_text(name: str, category_id: str = "", extra: str = "") -> str:
    return " ".join(p for p in (name, extra) if p)


class HashingEmbedder:
    """Signed feature hashing (blake2b) → vector L2-normalized, dim cố định."""

    def __init__(self, dim: int = 256):
        self.dim = dim

    def _index(self, feature: str) -> tuple[int, float]:
        h = int.from_bytes(hashlib.blake2b(feature.encode(), digest_size=8).digest(), "big")
        return h % self.dim, 1.0 if (h >> 63) & 1 else -1.0

    def embed_one(self, name: str, category_id: str = "") -> list[float]:
        v = [0.0] * self.dim
        for t in tokens(name):
            i, sign = self._index("t:" + t)
            v[i] += sign
        if category_id:
            # Danh mục là tín hiệu mạnh: cùng danh mục thì gần nhau hơn.
            for k in range(3):
                i, sign = self._index(f"c{k}:{category_id}")
                v[i] += 2.0 * sign
        n = math.sqrt(sum(x * x for x in v))
        return [x / n for x in v] if n else v

    async def embed(self, items: list[tuple[str, str]]) -> list[list[float]]:
        return [self.embed_one(name, cat) for name, cat in items]


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def weighted_mean(vectors: list[tuple[list[float], float]]) -> list[float] | None:
    if not vectors:
        return None
    dim = len(vectors[0][0])
    acc = [0.0] * dim
    total = 0.0
    for vec, w in vectors:
        total += w
        for i, x in enumerate(vec):
            acc[i] += w * x
    if total <= 0:
        return None
    acc = [x / total for x in acc]
    n = math.sqrt(sum(x * x for x in acc))
    return [x / n for x in acc] if n else None
