# syntax=docker/dockerfile:1
# Build context: thư mục gốc platform (backend/app/ecommerce)
#   docker build -f services/shipping-service/Dockerfile -t congthanhapp/shipping-service .
# CI dùng ko (xem .github/workflows/ci-go.yml) để build image không cần Dockerfile.
FROM golang:1.26-alpine AS build
ENV GOWORK=off CGO_ENABLED=0
WORKDIR /src
COPY libs/go libs/go
COPY services/shipping-service services/shipping-service
WORKDIR /src/services/shipping-service
RUN --mount=type=cache,target=/go/pkg/mod go mod download
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go build -trimpath -ldflags="-s -w" -o /out/app ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/app /app
EXPOSE 5016 9097
USER nonroot:nonroot
ENTRYPOINT ["/app"]
