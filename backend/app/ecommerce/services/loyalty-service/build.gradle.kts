plugins {
    id("congthanh.spring-service")
}

description = "Loyalty service (hexagonal): points lots (pending → available → expired), redemptions, tiers"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
}
