"""gunicorn: worker gthread (DRF đồng bộ, I/O chủ yếu là Postgres)."""

import os

bind = f"0.0.0.0:{os.getenv('HTTP_PORT', '5019')}"
workers = int(os.getenv("WEB_CONCURRENCY", "2"))
worker_class = "gthread"
threads = int(os.getenv("GUNICORN_THREADS", "4"))
timeout = 30
graceful_timeout = 20
accesslog = None  # log truy cập do gateway/OTel lo
forwarded_allow_ips = "*"
