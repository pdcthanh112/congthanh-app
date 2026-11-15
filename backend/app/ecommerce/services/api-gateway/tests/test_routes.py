"""Kiểm tra routes.yaml trước khi sinh cấu hình APISIX.

  python3 -I -m unittest discover -s services/api-gateway/tests -v
"""
import pathlib
import subprocess
import sys
import unittest

import yaml

GATEWAY = pathlib.Path(__file__).resolve().parents[1]
ROOT = GATEWAY.parents[1]
CFG = yaml.safe_load((GATEWAY / "routes.yaml").read_text())
ROUTES = CFG["routes"]
ALLOWED_KEYS = {
    "name", "service", "port", "paths", "auth", "exact", "websocket", "priority", "rewrite", "rate_limit", "max_conn",
}


class RoutesTest(unittest.TestCase):
    def test_names_are_unique(self):
        names = [r["name"] for r in ROUTES]
        self.assertEqual(sorted(names), sorted(set(names)))

    def test_required_fields_and_known_keys(self):
        for r in ROUTES:
            with self.subTest(route=r.get("name")):
                for key in ("name", "service", "port", "paths"):
                    self.assertIn(key, r)
                self.assertFalse(set(r) - ALLOWED_KEYS, "khoá lạ: generator sẽ bỏ qua nên dễ tưởng là có tác dụng")
                self.assertIn(r.get("auth", "optional"), ("required", "optional"))
                self.assertTrue(r["paths"])
                for p in r["paths"]:
                    self.assertTrue(p.startswith("/"), p)
                    self.assertFalse(p.endswith("/") and p != "/", p)

    def test_same_path_needs_different_priority(self):
        seen = {}
        for r in ROUTES:
            for p in r["paths"]:
                key = (p, bool(r.get("exact")), r.get("priority", 0))
                self.assertNotIn(key, seen, f"{p}: {r['name']} trùng với {seen.get(key)}")
                seen[key] = r["name"]

    def test_upstream_is_a_compose_service_with_matching_port(self):
        compose = yaml.safe_load((ROOT / "deploy/local/docker-compose.yaml").read_text())["services"]
        for r in ROUTES:
            with self.subTest(route=r["name"]):
                self.assertIn(r["service"], compose)

    def test_rate_limits_are_positive(self):
        limits = [CFG["defaults"]["rate_limit"]] + [r["rate_limit"] for r in ROUTES if "rate_limit" in r]
        for rl in limits:
            self.assertGreater(rl.get("count", 1), 0)
            self.assertGreater(rl.get("window", 1), 0)
            self.assertIn(rl.get("key", "ip"), ("ip", "consumer"))

    def test_consumer_rate_limit_requires_auth(self):
        # Giới hạn theo người dùng chỉ có nghĩa khi gateway đã xác thực token.
        for r in ROUTES:
            if r.get("rate_limit", {}).get("key") == "consumer":
                self.assertEqual(r.get("auth"), "required", r["name"])

    def test_generated_files_are_up_to_date(self):
        res = subprocess.run(
            [sys.executable, str(GATEWAY / "scripts/gen-routes.py"), "--check"], capture_output=True, text=True
        )
        self.assertEqual(res.returncode, 0, res.stdout + res.stderr)


if __name__ == "__main__":
    unittest.main()
