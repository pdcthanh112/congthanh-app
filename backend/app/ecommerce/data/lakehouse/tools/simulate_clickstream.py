"""Sinh dữ liệu clickstream giả để thử lakehouse khi chưa có frontend gửi event (chỉ dùng thư viện chuẩn).

    python data/lakehouse/tools/simulate_clickstream.py --sessions 200
    python data/lakehouse/tools/simulate_clickstream.py --url http://localhost:5029 --products p-1,p-2

Mỗi phiên đi qua phễu: page_view → (search) → product_view → add_to_cart → begin_checkout → purchase,
mỗi bước có xác suất rơi rụng, nên gold.funnel_daily ra tỉ lệ chuyển đổi giống thật.
"""

from __future__ import annotations

import argparse
import json
import random
import urllib.request
import uuid
from datetime import UTC, datetime, timedelta

QUERIES = ["áo thun", "giày chạy bộ", "tai nghe", "balo", "đồng hồ", "laptop"]


def event(kind: str, session: str, anon: str, customer: str | None, at: datetime, **extra) -> dict:
    e = {
        "event_id": str(uuid.uuid4()),
        "event_type": kind,
        "occurred_at": at.isoformat().replace("+00:00", "Z"),
        "session_id": session,
        "anonymous_id": anon,
    }
    if customer:
        e["customer_id"] = customer
    e.update({k: v for k, v in extra.items() if v is not None})
    return e


def session_events(products: list[str], customers: list[str]) -> list[dict]:
    session, anon = str(uuid.uuid4()), str(uuid.uuid4())
    customer = random.choice(customers) if random.random() < 0.6 else None
    at = datetime.now(UTC) - timedelta(minutes=random.randint(0, 600))
    out = [event("page_view", session, anon, customer, at, page_url="https://shop.congthanh.vn/")]

    def step() -> datetime:
        nonlocal at
        at += timedelta(seconds=random.randint(5, 90))
        return at

    if random.random() < 0.5:
        out.append(event("search", session, anon, customer, step(), search_query=random.choice(QUERIES)))
    if random.random() > 0.8:
        return out
    product = random.choice(products)
    price = random.choice(["9.99", "19.99", "49.99", "129.00"])
    out.append(event("product_view", session, anon, customer, step(), product_id=product))
    if random.random() > 0.35:
        return out
    qty = random.randint(1, 3)
    out.append(
        event(
            "add_to_cart",
            session,
            anon,
            customer,
            step(),
            product_id=product,
            quantity=qty,
            price=price,
            currency="USD",
        )
    )
    if random.random() > 0.6:
        return out
    out.append(event("begin_checkout", session, anon, customer, step()))
    if random.random() > 0.7:
        return out
    out.append(event("purchase", session, anon, customer, step(), order_id=f"ORD-{uuid.uuid4().hex[:10]}"))
    return out


def send(url: str, events: list[dict]) -> dict:
    req = urllib.request.Request(
        f"{url}/v1/events",
        data=json.dumps({"events": events}).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "simulate-clickstream/1.0"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--url", default="http://localhost:5029")
    p.add_argument("--sessions", type=int, default=100)
    p.add_argument("--products", default=",".join(f"p-{i}" for i in range(1, 21)))
    p.add_argument("--customers", type=int, default=50)
    a = p.parse_args()
    products = a.products.split(",")
    customers = [f"customer-{i}" for i in range(1, a.customers + 1)]
    batch: list[dict] = []
    accepted = rejected = 0
    for _ in range(a.sessions):
        batch.extend(session_events(products, customers))
        if len(batch) >= 80:
            r = send(a.url, batch)
            accepted, rejected, batch = accepted + r["accepted"], rejected + len(r["rejected"]), []
    if batch:
        r = send(a.url, batch)
        accepted, rejected = accepted + r["accepted"], rejected + len(r["rejected"])
    print(f"đã gửi {a.sessions} phiên: {accepted} event nhận, {rejected} bị loại")


if __name__ == "__main__":
    main()
