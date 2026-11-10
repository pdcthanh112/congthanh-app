plugins {
    `java-library`
    id("congthanh.java-conventions")
}

description = "Shared Spring Boot building blocks for the Java services (responses, exceptions, logging, CORS)"

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.apache.commons:commons-lang3")
    implementation("tools.jackson.core:jackson-databind")
}
