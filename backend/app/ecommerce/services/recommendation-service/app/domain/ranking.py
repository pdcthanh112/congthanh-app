"""Xếp hạng gợi ý: thuần (không I/O)."""

from __future__ import annotations

import math
from dataclasses import dataclass
from enum import StrEnum


class Kind(StrEnum):
    VIEW = "VIEW"
    CART = "CART"
    WISHLIST = "WISHLIST"
    PURCHASE = "PURCHASE"


WEIGHTS = {Kind.VIEW: 1.0, Kind.CART: 3.0, Kind.WISHLIST: 3.0, Kind.PURCHASE: 5.0}


class Reason(StrEnum):
    BOUGHT_TOGETHER = "BOUGHT_TOGETHER"
    SIMILAR = "SIMILAR"
    TRENDING = "TRENDING"
    FOR_YOU = "FOR_YOU"


@dataclass(frozen=True)
class Scored:
    product_id: str
    score: float
    reason: Reason
    category_id: str = ""


def pair_score(count: int, freq_a: int, freq_b: int) -> float:
    """Độ tương quan mua cùng (cosine): không thiên vị sản phẩm bán chạy như đếm thô."""
    if count <= 0 or freq_a <= 0 or freq_b <= 0:
        return 0.0
    return count / math.sqrt(freq_a * freq_b)


def decay(age_hours: float, half_life_hours: float = 72.0) -> float:
    return 0.5 ** (max(age_hours, 0.0) / half_life_hours)


def _normalize(items: list[Scored]) -> list[Scored]:
    if not items:
        return []
    top = max(i.score for i in items) or 1.0
    return [Scored(i.product_id, i.score / top, i.reason, i.category_id) for i in items]


def blend(
    sources: list[tuple[list[Scored], float]],
    exclude: set[str],
    limit: int,
    per_category: int = 3,
) -> list[Scored]:
    """Gộp nhiều nguồn (mỗi nguồn chuẩn hoá về [0,1] rồi nhân trọng số), bỏ sản phẩm đã tương tác,
    giới hạn số sản phẩm mỗi danh mục để danh sách đa dạng. Lý do lấy từ nguồn đóng góp nhiều nhất."""
    acc: dict[str, float] = {}
    best: dict[str, tuple[float, Scored]] = {}
    for items, weight in sources:
        for s in _normalize(items):
            if s.product_id in exclude:
                continue
            contrib = s.score * weight
            acc[s.product_id] = acc.get(s.product_id, 0.0) + contrib
            if s.product_id not in best or contrib > best[s.product_id][0]:
                best[s.product_id] = (contrib, s)
    ranked = sorted(acc.items(), key=lambda kv: (-kv[1], kv[0]))
    out: list[Scored] = []
    per_cat: dict[str, int] = {}
    overflow: list[Scored] = []
    for pid, score in ranked:
        src = best[pid][1]
        item = Scored(pid, round(score, 6), src.reason, src.category_id)
        cat = src.category_id
        if cat and per_cat.get(cat, 0) >= per_category:
            overflow.append(item)
            continue
        per_cat[cat] = per_cat.get(cat, 0) + 1
        out.append(item)
        if len(out) == limit:
            return out
    # Không đủ thì lấy thêm từ phần bị giới hạn danh mục.
    return (out + overflow)[:limit]
