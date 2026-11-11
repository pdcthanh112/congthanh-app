# syntax=docker/dockerfile:1
# Build context: backend/app/ecommerce
#   docker build -f services/promotion-service/Dockerfile -t congthanhapp/promotion-service .
# (CI dùng Jib: ./gradlew :services:promotion-service:jib — không cần Dockerfile)
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
RUN --mount=type=cache,target=/root/.gradle ./gradlew :services:promotion-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 65532 app
USER app
WORKDIR /app
COPY --from=build /workspace/services/promotion-service/build/libs/promotion-service.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
