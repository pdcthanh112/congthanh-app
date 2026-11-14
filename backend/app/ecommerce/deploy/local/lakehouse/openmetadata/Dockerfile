FROM python:3.12-slim
RUN pip install --no-cache-dir "openmetadata-ingestion[trino,dbt]==2.0.7.2"
COPY ingest.py /opt/openmetadata/ingest.py
ENTRYPOINT ["python", "/opt/openmetadata/ingest.py"]
