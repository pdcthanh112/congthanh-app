plugins {
    id("congthanh.spring-service")
}

description = "Returns service (hexagonal): RMA lifecycle, return policy, inspection, partial refunds via a Temporal workflow"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    testImplementation(libs.temporal.testing)
}
