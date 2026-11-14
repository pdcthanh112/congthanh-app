plugins {
    id("congthanh.spring-service")
}

description = "Stored-value service (hexagonal): store credit balances, holds for checkout, credits from returns"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
