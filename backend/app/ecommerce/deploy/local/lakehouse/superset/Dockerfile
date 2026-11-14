FROM apache/superset:6.1.0
USER root
RUN uv pip install --python /app/.venv/bin/python --no-cache "psycopg2-binary==2.9.10" "trino[sqlalchemy]==0.336.0"
USER superset
