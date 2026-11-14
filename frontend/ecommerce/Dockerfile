# syntax=docker/dockerfile:1
FROM node:22-alpine AS base
# https://github.com/nodejs/docker-node#nodealpine
RUN apk add --no-cache libc6-compat
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

# Cài dependency theo package-lock.json (dự án dùng npm).
FROM base AS deps
COPY package.json package-lock.json .npmrc* ./
RUN npm ci --ignore-scripts

FROM base AS builder
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Biến NEXT_PUBLIC_* được nhúng lúc build: truyền bằng --build-arg khi cần.
ARG NEXT_PUBLIC_GATEWAY_URL
ARG SENTRY_AUTH_TOKEN
ENV NEXT_PUBLIC_GATEWAY_URL=$NEXT_PUBLIC_GATEWAY_URL
RUN npm run build

FROM base AS runner
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
# output: 'standalone' tạo .next/standalone/server.js cùng node_modules tối thiểu.
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
