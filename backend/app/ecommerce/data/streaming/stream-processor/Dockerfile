# syntax=docker/dockerfile:1
# Build context: backend/app/ecommerce
#   docker build -f data/streaming/stream-processor/Dockerfile -t congthanhapp/stream-processor .
# Image = Flink + /opt/flink/usrlib (jar job + connector Kafka + protobuf); chạy application mode qua Flink Operator.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY api ./api
COPY libs/java ./libs/java
COPY services/product-service ./services/product-service
COPY services/order-service ./services/order-service
COPY services/payment-service ./services/payment-service
COPY services/promotion-service ./services/promotion-service
COPY services/checkout-service ./services/checkout-service
COPY data/streaming/stream-processor ./data/streaming/stream-processor
RUN --mount=type=cache,target=/root/.gradle ./gradlew :data:streaming:stream-processor:flinkUsrlib --no-daemon -x test

FROM flink:2.2.1-java21
# Plugin S3 cho checkpoint/savepoint (có sẵn trong image, chỉ cần bật).
RUN mkdir -p /opt/flink/plugins/s3-fs-presto \
 && cp /opt/flink/opt/flink-s3-fs-presto-*.jar /opt/flink/plugins/s3-fs-presto/
COPY --from=build --chown=flink:flink /workspace/data/streaming/stream-processor/build/flink/usrlib /opt/flink/usrlib
# Entrypoint của image Flink tự hạ quyền xuống user flink (gosu); trên k8s chạy thẳng uid 9999.
