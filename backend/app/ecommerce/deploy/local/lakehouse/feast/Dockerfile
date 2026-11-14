FROM python:3.12-slim
RUN pip install --no-cache-dir "feast[trino,redis]==0.67.0"
WORKDIR /feature_repo
ENV FEAST_REGISTRY_PATH=/data/registry.db \
    FEAST_TRINO_HOST=trino \
    FEAST_TRINO_PORT=8080 \
    FEAST_REDIS=redis:6379,db=3
