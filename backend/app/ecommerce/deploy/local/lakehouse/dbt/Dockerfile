FROM python:3.12-slim
RUN pip install --no-cache-dir "dbt-core==1.12.7" "dbt-trino==1.11.0" "openlineage-dbt==1.55.0"

RUN mkdir -p /opt/lakehouse/dbt-target && chown 50000:0 /opt/lakehouse/dbt-target && chmod 775 /opt/lakehouse/dbt-target
USER 50000:0
WORKDIR /opt/lakehouse/dbt
ENTRYPOINT ["dbt-ol"]
CMD ["build"]
