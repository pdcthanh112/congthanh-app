# syntax=docker/dockerfile:1
# Image chung cho service Python (FastAPI/Django, quản lý bằng uv). Build context: backend/app/ecommerce
#   docker build -f services/python.Dockerfile --build-arg SERVICE=translate-service -t congthanhapp/translate-service .
# Mặc định chạy `python -m app` (service tự đọc cổng từ cấu hình); Django ghi đè CMD bằng gunicorn.
ARG PYTHON_VERSION=3.13

FROM ghcr.io/astral-sh/uv:0.9.5 AS uv

FROM python:${PYTHON_VERSION}-slim-bookworm AS build
ARG SERVICE
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /src
COPY libs/python libs/python
# Lớp dependency (theo uv.lock) tách khỏi code để cache khi chỉ sửa code.
COPY services/${SERVICE}/pyproject.toml services/${SERVICE}/uv.lock services/${SERVICE}/
RUN --mount=type=cache,target=/root/.cache/uv \
    cd services/${SERVICE} && uv sync --locked --no-dev --no-install-project --no-editable
COPY services/${SERVICE} services/${SERVICE}
RUN --mount=type=cache,target=/root/.cache/uv \
    cd services/${SERVICE} && uv sync --locked --no-dev --no-editable \
    && rm -rf tests .venv && cp -r . /app/src

FROM python:${PYTHON_VERSION}-slim-bookworm
RUN useradd --uid 65532 --no-create-home --shell /usr/sbin/nologin nonroot
COPY --from=build --chown=65532:65532 /app /app
WORKDIR /app/src
ENV PATH=/app/.venv/bin:$PATH PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
USER 65532
ENTRYPOINT ["/app/.venv/bin/python", "-m"]
CMD ["app"]
