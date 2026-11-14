plugins {
    `java-library`
    id("congthanh.java-conventions")
}

description = "Shared foundation for the Java services: error model + RFC 9457 responses, access log, CORS, gRPC error mapping, JPA auditing"

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-starter-grpc-server")

    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly(libs.resilience4j.spring.boot4)
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation(libs.resilience4j.spring.boot4)
    testRuntimeOnly("com.h2database:h2")
}
