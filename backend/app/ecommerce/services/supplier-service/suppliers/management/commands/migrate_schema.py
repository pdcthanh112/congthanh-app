"""Tạo schema của service (nếu chưa có) rồi chạy migrate; dùng ở init container, có advisory lock
để nhiều pod khởi động cùng lúc không chạy migration chồng nhau."""

from django.conf import settings
from django.core.management import call_command
from django.core.management.base import BaseCommand
from django.db import connection

LOCK_ID = 0x5355_5050  # "SUPP"


class Command(BaseCommand):
    help = "CREATE SCHEMA IF NOT EXISTS + migrate (advisory lock)"

    def handle(self, *args, **opts):
        with connection.cursor() as c:
            c.execute("SELECT pg_advisory_lock(%s)", [LOCK_ID])
            try:
                c.execute(f'CREATE SCHEMA IF NOT EXISTS "{settings.DB_SCHEMA}"')
                call_command("migrate", interactive=False, verbosity=1)
            finally:
                c.execute("SELECT pg_advisory_unlock(%s)", [LOCK_ID])
