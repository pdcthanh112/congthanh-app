"""Cấu hình test: Postgres thật từ TEST_DATABASE_URL, schema public của database test, không pool, tắt JWT."""

import os

if url := os.getenv("TEST_DATABASE_URL"):
    os.environ["DATABASE_URL"] = url
os.environ.update(DB_SCHEMA="public", DB_POOL="false", JWT_JWKS_URL="")

from .settings import *  # noqa: F403
