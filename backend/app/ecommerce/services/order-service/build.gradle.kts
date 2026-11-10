plugins {
    id("congthanh.spring-service")
}

description = "Order service (hexagonal): checkout, order lifecycle; owns the Temporal checkout workflow"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(libs.temporal.testing)
}
