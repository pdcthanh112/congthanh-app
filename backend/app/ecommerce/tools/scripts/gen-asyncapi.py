#!/usr/bin/env python3
"""Sinh api/asyncapi/asyncapi.yaml từ api/proto/ecommerce/*/v1/*_events.proto.

  python3 tools/scripts/gen-asyncapi.py [--check]
"""
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "api/asyncapi/asyncapi.yaml"
PRODUCERS = {
    "inventory": "inventory-service", "cart": "cart-service", "order": "order-service",
    "payment": "payment-service", "product": "product-service", "promotion": "promotion-service",
    "catalog": "catalog-service", "review": "review-service", "customer": "customer-service", "message": "message-service",
}


def build():
    doc = {
        "asyncapi": "3.0.0",
        "info": {
            "title": "CongThanhApp e-commerce events", "version": "1.0.0",
            "description": "Event miền trên Kafka. Mỗi record là ecommerce.common.v1.EventEnvelope (Protobuf) bọc event "
                           "trong `payload` (Any). Header ce_type = tên đầy đủ của event, traceparent = W3C trace "
                           "context. Key = aggregate id.",
        },
        "defaultContentType": "application/x-protobuf",
        "servers": {
            "local": {"host": "localhost:29092", "protocol": "kafka"},
            "prod": {"host": "ecom-kafka-bootstrap.messaging.svc.cluster.local:9092", "protocol": "kafka",
                     "security": [{"type": "scramSha512"}]},
        },
        "channels": {}, "operations": {}, "components": {"messages": {}},
    }
    for f in sorted(ROOT.glob("api/proto/ecommerce/*/v1/*_events.proto")):
        dom = f.parts[-3]
        src = f.read_text()
        pkg = re.search(r"package ([\w.]+);", src).group(1)
        # Chỉ event cấp cao, bỏ message phụ (ReservedLine, OrderLine...).
        names = [n for n in re.findall(r"^message (\w+) \{", src, re.M) if not n.endswith("Line")]
        rel = f.relative_to(ROOT).as_posix()
        refs = {}
        for n in names:
            key = f"{dom}.{n}"
            doc["components"]["messages"][key] = {
                "name": n, "title": f"{pkg}.{n}",
                "payload": {"schemaFormat": "application/vnd.google.protobuf;version=3",
                            "schema": {"$ref": f"../../{rel}"}},
                "headers": {"type": "object", "properties": {
                    "ce_type": {"type": "string", "const": f"{pkg}.{n}"}, "traceparent": {"type": "string"}}},
            }
            refs[n] = {"$ref": f"#/components/messages/{key}"}
        producer = PRODUCERS.get(dom, dom)
        ch = f"{dom}Events"
        doc["channels"][ch] = {"address": f"ecom.{dom}.events", "messages": refs,
                               "description": f"Event của {producer}; DLQ: ecom.{dom}.events.dlq"}
        doc["operations"][f"publish{dom.capitalize()}Events"] = {
            "action": "send", "channel": {"$ref": f"#/channels/{ch}"},
            "summary": f"{producer} ghi qua outbox → Debezium"}
    return "# SINH từ api/proto/ecommerce/*/v1/*_events.proto bằng tools/scripts/gen-asyncapi.py\n" + \
        yaml.safe_dump(doc, sort_keys=False, allow_unicode=True)


def main():
    out = build()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != out:
            print("stale:", OUT)
            sys.exit(1)
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(out)
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
