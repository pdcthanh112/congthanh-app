plugins {
    `java-library`
    id("congthanh.java-conventions")
}

description = "Platform building blocks for the Java services: transactional outbox, idempotent consumers, Idempotency-Key for HTTP"

dependencies {
    api(project(":libs:java:proto"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.liquibase:liquibase-core")
    implementation("io.opentelemetry:opentelemetry-api")
    // Tuỳ chọn: chỉ bật khi service có trên classpath (order, payment).
    compileOnly(platform(libs.temporal.bom))
    compileOnly(libs.temporal.sdk)
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testRuntimeOnly("com.h2database:h2")
}
