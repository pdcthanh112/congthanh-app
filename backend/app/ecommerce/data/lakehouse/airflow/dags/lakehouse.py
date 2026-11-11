from __future__ import annotations

import os
from datetime import datetime, timedelta

from airflow.providers.standard.operators.bash import BashOperator
from airflow.sdk import DAG

JOBS = os.getenv("LAKEHOUSE_JOBS_DIR", "/opt/lakehouse/jobs")
PYTHON = os.getenv("LAKEHOUSE_PYTHON", "python")
DEFAULT_ARGS = {"retries": 2, "retry_delay": timedelta(minutes=2), "execution_timeout": timedelta(minutes=30)}


def job(task_id: str, script: str) -> BashOperator:
    return BashOperator(task_id=task_id, bash_command=f"{PYTHON} {JOBS}/{script}")


with DAG(
    dag_id="lakehouse_silver_gold",
    schedule="@hourly",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=DEFAULT_ARGS,
    tags=["lakehouse"],
):
    job("silver", "silver.py") >> job("quality_checks", "checks.py") >> job("gold", "gold.py")

with DAG(
    dag_id="lakehouse_maintenance",
    schedule="@daily",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=DEFAULT_ARGS,
    tags=["lakehouse"],
):
    job("maintenance", "maintenance.py")
