"""Định danh dùng chung cho service Python (cùng công thức với Java common-core và Go idgen)."""

from __future__ import annotations

import hashlib
import os
import threading
import time
import uuid

EPOCH_MS = 1609459200000  # 2021-01-01T00:00:00Z, cùng epoch với Java Snowflake
_NODE_BITS = 10
_SEQ_BITS = 12
MAX_NODE = (1 << _NODE_BITS) - 1
_SEQ_MASK = (1 << _SEQ_BITS) - 1


def _node_from_env() -> int:
    raw = os.environ.get("NODE_ID", "").strip()
    if raw:
        node = int(raw)
        if not 0 <= node <= MAX_NODE:
            raise ValueError(f"NODE_ID phải trong 0..{MAX_NODE}, nhận {node}")
        return node
    host = os.environ.get("HOSTNAME") or str(os.getpid())
    return int.from_bytes(hashlib.sha256(host.encode()).digest()[:4], "big") % (MAX_NODE + 1)


class Snowflake:
    """41 bit mili-giây từ 2021-01-01, 10 bit node (NODE_ID), 12 bit sequence; lưu bằng BIGINT."""

    def __init__(self, node: int, clock=lambda: time.time_ns() // 1_000_000):
        if not 0 <= node <= MAX_NODE:
            raise ValueError(f"node phải trong 0..{MAX_NODE}")
        self.node = node
        self._clock = clock
        self._last = -1
        self._seq = 0
        self._lock = threading.Lock()

    def next(self) -> int:
        with self._lock:
            now = max(self._clock(), self._last)  # đồng hồ lùi: giữ mốc cũ
            if now == self._last:
                self._seq = (self._seq + 1) & _SEQ_MASK
                if self._seq == 0:
                    while now <= self._last:
                        now = self._clock()
            else:
                self._seq = 0
            self._last = now
            return ((now - EPOCH_MS) << (_NODE_BITS + _SEQ_BITS)) | (self.node << _SEQ_BITS) | self._seq


_DEFAULT = Snowflake(_node_from_env())


def snowflake() -> str:
    """Id thực thể kế tiếp, dạng chuỗi số (domain giữ str; DB lưu BIGINT, SQL ép bằng ``$n::text::bigint``)."""
    return str(_DEFAULT.next())


def event_id() -> str:
    """Id của event/outbox (UUID ngẫu nhiên như OutboxWriter bên Java), không phải id thực thể."""
    return str(uuid.uuid4())


def erased_id(subject_id: str) -> str:
    """Định danh thay thế ổn định, không đảo ngược cho dữ liệu giữ lại sau yêu cầu xoá (ADR 0027)."""
    return "erased-" + hashlib.sha256(subject_id.encode()).hexdigest()[:16]
