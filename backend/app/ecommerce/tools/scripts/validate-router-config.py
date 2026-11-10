#!/usr/bin/env python3
"""Kiểm tra platform/graphql/router/config.yaml theo JSON schema chính thức của Cosmo Router.

Schema tải về theo đúng phiên bản router đang dùng (ROUTER_VERSION), nên lỗi gõ sai khoá cấu hình
bị bắt trong CI thay vì lúc router khởi động.
    python3 tools/scripts/validate-router-config.py [--schema đường-dẫn-schema.json]
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

import jsonschema
import yaml

ROUTER_VERSION = "0.350.0"
ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "platform" / "graphql" / "router" / "config.yaml"
URL = f"https://raw.githubusercontent.com/wundergraph/cosmo/router@{ROUTER_VERSION}/router/pkg/config/config.schema.json"


def main() -> int:
    if "--schema" in sys.argv:
        schema = json.loads(Path(sys.argv[sys.argv.index("--schema") + 1]).read_text())
    else:
        with urllib.request.urlopen(URL, timeout=30) as r:  # noqa: S310 (URL cố định)
            schema = json.load(r)
    # Router chạy os.ExpandEnv trên file trước khi kiểm schema; mô phỏng với giá trị mẫu.
    sample = {
        "ROUTER_JWKS_URL": "http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs",
        "OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-collector:4317",
        "ROUTER_CORS_ORIGINS": '"http://localhost:3000"',
    }
    text = re.sub(r"\$\{(\w+)\}", lambda m: sample.get(m.group(1), ""), CONFIG.read_text())
    config = yaml.safe_load(text)
    errors = sorted(jsonschema.Draft202012Validator(schema).iter_errors(config), key=lambda e: list(e.path))
    for e in errors:
        print(f"✗ {'.'.join(map(str, e.path)) or '<root>'}: {e.message}")
    if not errors:
        print(f"✓ router config hợp lệ (Cosmo Router {ROUTER_VERSION})")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
