# syntax=docker/dockerfile:1
# Build context: thư mục gốc platform (backend/app/ecommerce)
#   docker build -f services/inventory-service/Dockerfile -t congthanhapp/inventory-service .
# CI dùng ko (xem .github/workflows/ci-go.yml) để build image không cần Dockerfile.
FROM golang:1.26-alpine AS build
ENV GOWORK=off CGO_ENABLED=0
WORKDIR /src
COPY libs/go libs/go
COPY services/inventory-service services/inventory-service
WORKDIR /src/services/inventory-service
RUN --mount=type=cache,target=/go/pkg/mod go mod download
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go build -trimpath -ldflags="-s -w" -o /out/app ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/app /app
EXPOSE 5014 9092
USER nonroot:nonroot
ENTRYPOINT ["/app"]
