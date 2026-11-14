from __future__ import annotations

import os
from datetime import UTC, datetime, timedelta

from airflow.providers.standard.operators.bash import BashOperator
from airflow.sdk import DAG

JOBS = os.getenv("LAKEHOUSE_JOBS_DIR", "/opt/lakehouse/jobs")
DBT_DIR = os.getenv("LAKEHOUSE_DBT_DIR", "/opt/lakehouse/dbt")
PYTHON = os.getenv("LAKEHOUSE_PYTHON", "python")
DBT_BIN = os.getenv("LAKEHOUSE_DBT_BIN", "/opt/airflow/dbt-venv/bin")

DBT_TARGET = os.getenv("LAKEHOUSE_DBT_TARGET", "/opt/lakehouse/dbt-target")
DBT_ARGS = f"--project-dir {DBT_DIR} --profiles-dir {DBT_DIR} --log-path /tmp/dbt/logs"
FEAST_URL = os.getenv("LAKEHOUSE_FEAST_URL", "http://feast:6566")

FEAST_MATERIALIZE = f"""
if ! curl -sf {FEAST_URL}/health > /dev/null; then echo "Feast không chạy (profile ml tắt), bỏ qua"; exit 0; fi
curl -sf -X POST {FEAST_URL}/materialize-incremental -H 'Content-Type: application/json' \\
  -d "{{\\"end_ts\\": \\"$(date -u +%Y-%m-%dT%H:%M:%S)\\"}}"
echo "Feast: materialize xong"
"""
DEFAULT_ARGS = {"retries": 2, "retry_delay": timedelta(minutes=2), "execution_timeout": timedelta(minutes=30)}


def job(task_id: str, script: str) -> BashOperator:
    return BashOperator(task_id=task_id, bash_command=f"{PYTHON} {JOBS}/{script}")


def dbt(task_id: str, command: str, target: str = DBT_TARGET, **kwargs) -> BashOperator:
    return BashOperator(
        task_id=task_id,
        bash_command=f"dbt-ol {command} {DBT_ARGS} --target-path {target}",
        env={"PATH": f"{DBT_BIN}:{os.environ.get('PATH', '/usr/bin:/bin')}"},
        append_env=True,
        **kwargs,
    )


with DAG(
    dag_id="lakehouse_silver_gold",
    schedule="@hourly",
    start_date=datetime(2026, 1, 1, tzinfo=UTC),
    catchup=False,
    max_active_runs=1,
    default_args=DEFAULT_ARGS,
    tags=["lakehouse", "dbt"],
):
    (
        job("decode_events", "decode_events.py")
        >> dbt("dbt_build", "build")
        >> [
            BashOperator(task_id="feast_materialize", bash_command=FEAST_MATERIALIZE),
            dbt("dbt_docs", "docs generate"),
        ]
    )

    dbt("source_freshness", "source freshness", target="/tmp/dbt/freshness", retries=0)

with DAG(
    dag_id="lakehouse_maintenance",
    schedule="@daily",
    start_date=datetime(2026, 1, 1, tzinfo=UTC),
    catchup=False,
    max_active_runs=1,
    default_args=DEFAULT_ARGS,
    tags=["lakehouse"],
):
    job("maintenance", "maintenance.py")
