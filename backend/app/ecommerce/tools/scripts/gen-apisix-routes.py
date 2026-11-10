#!/usr/bin/env python3
"""Sinh cấu hình APISIX từ platform/gateway/routes.yaml.

  python3 tools/scripts/gen-apisix-routes.py [--check]

--check: chỉ so sánh, trả mã 1 nếu file sinh ra đã cũ (dùng trong CI).
"""
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "platform/gateway/routes.yaml"
OUT_STANDALONE = ROOT / "platform/gateway/apisix/apisix.yaml"
OUT_K8S = ROOT / "platform/gateway/apisix/k8s/routes.yaml"
HEADER = "# FILE SINH TỰ ĐỘNG từ platform/gateway/routes.yaml — đừng sửa tay.\n"


def plugins(r, cfg, k8s):
    rl = {**cfg["defaults"]["rate_limit"], **r.get("rate_limit", {})}
    key = "consumer_name" if rl.get("key") == "consumer" else "remote_addr"
    p = {
        "limit-count": {
            "count": rl["count"], "time_window": rl["window"], "key": key, "rejected_code": 429,
            "policy": "redis", "redis_host": "valkey" if not k8s else "valkey.data.svc.cluster.local",
            "allow_degradation": True,
        },
    }
    if "max_conn" in r:
        p["limit-conn"] = {"conn": r["max_conn"], "burst": r["max_conn"] // 10, "default_conn_delay": 0.1, "key": "server_addr", "rejected_code": 503}
    if r.get("auth") == "required":
        kc = cfg["keycloak"]
        p["openid-connect"] = {
            "discovery": kc["discovery"] if not k8s else kc["discovery"].replace("keycloak:8080", "keycloak.identity.svc.cluster.local"),
            "client_id": kc["client_id"], "client_secret": "$secret://vault/apisix/keycloak-client-secret" if k8s else "$ENV://KEYCLOAK_GATEWAY_SECRET",
            "bearer_only": True, "use_jwks": True, "set_userinfo_header": False,
        }
    if "rewrite" in r:
        p["proxy-rewrite"] = {"uri": r["rewrite"]}
    return p


def timeouts(r):
    # Subscription giữ kết nối lâu: graphql-ws ping định kỳ, nên chỉ cắt khi im lặng quá 1 giờ.
    idle = 3600 if r.get("websocket") else 30
    return {"connect": 3, "send": idle, "read": idle}


def uris(r):
    return list(r["paths"]) if r.get("exact") else [p + "*" for p in r["paths"]]


def standalone(cfg):
    routes, upstreams = [], []
    for r in cfg["routes"]:
        upstreams.append({"id": r["name"], "type": "roundrobin", "scheme": "http",
                          "nodes": {f'{r["service"]}:{r["port"]}': 1}, "timeout": timeouts(r)})
        route = {"id": r["name"], "uris": uris(r), "upstream_id": r["name"], "plugins": plugins(r, cfg, False)}
        if r.get("websocket"):
            route["enable_websocket"] = True
        routes.append(route)
    return {"routes": routes, "upstreams": upstreams}


def k8s(cfg):
    docs = []
    for r in cfg["routes"]:
        docs.append({
            "apiVersion": "apisix.apache.org/v2", "kind": "ApisixRoute",
            "metadata": {"name": r["name"], "namespace": "gateway"},
            "spec": {"ingressClassName": "apisix", "http": [{
                "name": r["name"],
                "match": {"paths": uris(r)},
                **({"websocket": True} if r.get("websocket") else {}),
                "backends": [{"serviceName": r["service"], "servicePort": 80, "resolveGranularity": "service"}],
                "plugins": [{"name": k, "enable": True, "config": v} for k, v in plugins(r, cfg, True).items()],
            }]},
        })
    return docs


def main():
    cfg = yaml.safe_load(SRC.read_text())
    st = HEADER + yaml.safe_dump(standalone(cfg), sort_keys=False, allow_unicode=True) + "#END\n"
    ks = HEADER + yaml.safe_dump_all(k8s(cfg), sort_keys=False, allow_unicode=True)
    if "--check" in sys.argv:
        stale = [p for p, c in ((OUT_STANDALONE, st), (OUT_K8S, ks)) if not p.exists() or p.read_text() != c]
        if stale:
            print("stale:", *stale)
            sys.exit(1)
        return
    OUT_K8S.parent.mkdir(parents=True, exist_ok=True)
    OUT_STANDALONE.write_text(st)
    OUT_K8S.write_text(ks)
    print("wrote", OUT_STANDALONE.relative_to(ROOT), OUT_K8S.relative_to(ROOT))


if __name__ == "__main__":
    main()
