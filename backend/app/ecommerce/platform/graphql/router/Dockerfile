# syntax=docker/dockerfile:1.10
# Image router cho một graph: Cosmo Router + config dùng chung + execution config của graph đó.
#   npm run supergraph   (sinh platform/graphql/<graph>/config.json)
#   docker build -f platform/graphql/router/Dockerfile --build-arg GRAPH=storefront -t graphql-router platform/graphql
ARG ROUTER_VERSION=0.350.0
FROM ghcr.io/wundergraph/cosmo/router:${ROUTER_VERSION}
ARG GRAPH
COPY router/config.yaml /config/router.yaml
COPY ${GRAPH}/config.json /config/config.json
ENV CONFIG_PATH=/config/router.yaml \
    LISTEN_ADDR=0.0.0.0:4000
EXPOSE 4000 8088
USER 65532:65532
