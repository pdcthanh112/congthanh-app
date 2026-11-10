"""Cấu hình Django đọc từ biến môi trường (12-factor), cùng quy ước với service Go/Node."""

import os
import re
from urllib.parse import unquote, urlparse

from ecom_platform import logging as eplog


def _db(url: str, schema: str) -> dict:
    u = urlparse(url)
    return {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": u.path.lstrip("/"),
        "USER": unquote(u.username or ""),
        "PASSWORD": unquote(u.password or ""),
        "HOST": u.hostname or "localhost",
        "PORT": u.port or 5432,
        # Mỗi service một schema riêng trong cùng cụm Postgres.
        "OPTIONS": {"options": f"-c search_path={schema}"}
        | ({"pool": {"min_size": 2, "max_size": 10}} if os.getenv("DB_POOL", "true") == "true" else {}),
        "CONN_HEALTH_CHECKS": True,
        "TEST": {"NAME": os.getenv("TEST_DATABASE_NAME")},
    }


DB_SCHEMA = os.getenv("DB_SCHEMA", "supplier")
if not re.fullmatch(r"[a-z_][a-z0-9_]*", DB_SCHEMA):
    raise RuntimeError("DB_SCHEMA must be a lowercase identifier")

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "dev-only-not-secret")
DEBUG = os.getenv("DJANGO_DEBUG", "false").lower() == "true"
ALLOWED_HOSTS = os.getenv("ALLOWED_HOSTS", "*").split(",")
ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"
USE_TZ = True
TIME_ZONE = "UTC"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# API thuần: không admin/session/template; xác thực bằng JWT Keycloak (suppliers.auth).
INSTALLED_APPS = ["django.contrib.contenttypes", "rest_framework", "suppliers"]
MIDDLEWARE = ["django.middleware.security.SecurityMiddleware", "django.middleware.common.CommonMiddleware"]

DATABASES = {
    "default": _db(
        os.getenv("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce"), DB_SCHEMA
    )
}

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": ["suppliers.auth.JWTAuthentication"],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.AllowAny"],
    "DEFAULT_RENDERER_CLASSES": ["suppliers.envelope.EnvelopeRenderer"],
    "DEFAULT_PARSER_CLASSES": ["rest_framework.parsers.JSONParser"],
    "EXCEPTION_HANDLER": "suppliers.envelope.exception_handler",
    "UNAUTHENTICATED_USER": None,
}

LOGGING_CONFIG = None
eplog.setup("supplier-service")
