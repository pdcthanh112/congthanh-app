# syntax=docker/dockerfile:1
# Image chung cho mọi service NestJS (npm workspaces). Build context: backend/app/ecommerce
#   docker build -f services/node.Dockerfile --build-arg SERVICE=catalog-service -t congthanhapp/catalog-service .
ARG NODE_VERSION=24

FROM node:${NODE_VERSION}-bookworm-slim AS deps
WORKDIR /src
# Chỉ package*.json trước để cache lớp npm ci khi code đổi.
COPY package.json package-lock.json ./
COPY --parents libs/ts/*/package.json services/*/package.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

FROM deps AS build
ARG SERVICE
COPY tsconfig.base.json buf.yaml buf.gen.yaml* ./
COPY api/proto api/proto
COPY libs/ts libs/ts
COPY services/${SERVICE} services/${SERVICE}
RUN npm run build -w @ecommerce/proto -w @ecommerce/shared -w @ecommerce/platform -w @ecommerce/clients -w @ecommerce/${SERVICE} && chmod -R a+rX /src/libs /src/services

FROM node:${NODE_VERSION}-bookworm-slim AS prod-deps
ARG SERVICE
WORKDIR /src
COPY package.json package-lock.json ./
COPY --parents libs/ts/*/package.json services/*/package.json ./
# Chỉ dependency production của service này (+ thư viện workspace nó dùng). npm giữ nguyên bố cục
# workspace: node_modules gốc, node_modules lồng (khi trùng tên khác phiên bản) và symlink @ecommerce/* → libs/ts.
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev --no-audit --no-fund -w @ecommerce/${SERVICE} --include-workspace-root=false \
    && chmod -R a+rX /src   # COPY --parents tạo thư mục cha quyền 700; user nonroot cần đọc được

FROM gcr.io/distroless/nodejs${NODE_VERSION}-debian12:nonroot
ARG SERVICE
ENV NODE_ENV=production
WORKDIR /app
COPY --from=prod-deps /src/ ./
COPY --from=build /src/libs/ts/proto-gen/dist ./libs/ts/proto-gen/dist
COPY --from=build /src/libs/ts/shared/dist ./libs/ts/shared/dist
COPY --from=build /src/libs/ts/platform/dist ./libs/ts/platform/dist
COPY --from=build /src/libs/ts/clients/dist ./libs/ts/clients/dist
COPY --from=build /src/services/${SERVICE}/dist ./services/${SERVICE}/dist
# drizzle/ chứa migration SQL; service không có DB thì glob không khớp và bước này bỏ qua.
COPY --from=build /src/services/${SERVICE}/drizzl[e] ./services/${SERVICE}/drizzle
WORKDIR /app/services/${SERVICE}
USER nonroot
# Telemetry phải nạp trước mọi module để auto-instrumentation patch được pg, http, kafka...
ENTRYPOINT ["/nodejs/bin/node", "--enable-source-maps", "--import", "@ecommerce/platform/telemetry", "dist/main.js"]
