FROM apache/airflow:3.1.2-python3.12
RUN python -m venv /opt/airflow/spark-client \
 && /opt/airflow/spark-client/bin/pip install --no-cache-dir "pyspark-client==4.0.1" \
 && python -m venv /opt/airflow/dbt-venv \
 && /opt/airflow/dbt-venv/bin/pip install --no-cache-dir "dbt-core==1.12.7" "dbt-trino==1.11.0" "openlineage-dbt==1.55.0"

USER root
RUN mkdir -p /opt/lakehouse/dbt-target && chown airflow:0 /opt/lakehouse/dbt-target && chmod 775 /opt/lakehouse/dbt-target
USER airflow
ENV LAKEHOUSE_PYTHON=/opt/airflow/spark-client/bin/python \
    LAKEHOUSE_DBT_BIN=/opt/airflow/dbt-venv/bin
