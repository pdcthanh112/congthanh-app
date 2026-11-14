from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

OM = os.getenv("OPENMETADATA_URL", "http://openmetadata:8585")
TARGET = Path(os.getenv("DBT_TARGET_DIR", "/dbt-target"))
SERVICE = "lakehouse_trino"


def api(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    req = urllib.request.Request(
        f"{OM}/api/v1{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def bot_token() -> str:
    for _ in range(60):  # server có thể vẫn đang khởi động
        try:
            password = base64.b64encode(b"admin").decode()
            admin = api("POST", "/users/login", body={"email": "admin@open-metadata.org", "password": password})
            break
        except OSError:
            time.sleep(5)
    else:
        sys.exit("Không đăng nhập được OpenMetadata")
    bot = api("GET", "/bots/name/ingestion-bot", admin["accessToken"])
    auth = api("GET", f"/users/auth-mechanism/{bot['botUser']['id']}", admin["accessToken"])
    return auth["config"]["JWTToken"]


def run(name: str, source: dict, token: str) -> None:
    workflow = {
        "source": source,
        "sink": {"type": "metadata-rest", "config": {}},
        "workflowConfig": {
            "loggerLevel": "INFO",
            "openMetadataServerConfig": {
                "hostPort": f"{OM}/api",
                "authProvider": "openmetadata",
                "securityConfig": {"jwtToken": token},
            },
        },
    }
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(workflow, f)
    print(f"== {name}", flush=True)
    subprocess.run(["metadata", "ingest", "-c", f.name], check=True)


def main() -> None:
    token = bot_token()
    run(
        "Trino (catalog lakehouse)",
        {
            "type": "trino",
            "serviceName": SERVICE,
            "serviceConnection": {
                "config": {
                    "type": "Trino",
                    "hostPort": os.getenv("TRINO_HOST_PORT", "trino:8080"),
                    "username": "openmetadata",
                    "catalog": "lakehouse",
                }
            },
            "sourceConfig": {
                "config": {
                    "type": "DatabaseMetadata",
                    "schemaFilterPattern": {"includes": ["bronze", "ods", "silver", "gold"]},
                    "includeViews": False,
                }
            },
        },
        token,
    )
    files = {k: TARGET / f"{k}.json" for k in ("manifest", "catalog", "run_results")}
    if not files["manifest"].exists():
        print(f"Bỏ qua dbt: chưa có {files['manifest']} (chạy dbt build + docs generate trước)")
        return
    run(
        "dbt (model, test, lineage)",
        {
            "type": "dbt",
            "serviceName": SERVICE,
            "sourceConfig": {
                "config": {
                    "type": "DBT",
                    "dbtConfigSource": {
                        "dbtConfigType": "local",
                        "dbtManifestFilePath": str(files["manifest"]),
                        **({"dbtCatalogFilePath": str(files["catalog"])} if files["catalog"].exists() else {}),
                        **(
                            {"dbtRunResultsFilePath": str(files["run_results"])}
                            if files["run_results"].exists()
                            else {}
                        ),
                    },
                    "dbtUpdateDescriptions": True,
                    "includeTags": True,
                }
            },
        },
        token,
    )
    print(f"OpenMetadata: đã nạp metadata vào service {SERVICE}. UI: http://localhost:8585")


if __name__ == "__main__":
    main()
