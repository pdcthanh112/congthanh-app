"""Cấu hình Superset local (mount vào /app/pythonpath). Key cố định, CHỈ dùng trên máy dev."""

import os

SECRET_KEY = os.getenv("SUPERSET_SECRET_KEY", "local-dev-superset-secret-change-me")
SQLALCHEMY_DATABASE_URI = os.getenv(
    "SUPERSET_DATABASE_URI", "postgresql+psycopg2://postgres:postgres@postgres:5432/superset"
)
# Không có Redis riêng cho Superset: cache trong RAM là đủ cho local.
CACHE_CONFIG = {"CACHE_TYPE": "SimpleCache", "CACHE_DEFAULT_TIMEOUT": 300}
DATA_CACHE_CONFIG = CACHE_CONFIG
WTF_CSRF_ENABLED = True
TALISMAN_ENABLED = False
FEATURE_FLAGS = {"DASHBOARD_RBAC": True}
BABEL_DEFAULT_LOCALE = "en"
ROW_LIMIT = 50_000
