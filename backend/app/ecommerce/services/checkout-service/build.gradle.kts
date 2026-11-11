plugins {
    id("congthanh.spring-native")
}

description = "Checkout service (hexagonal): checkout sessions, quotes (pricing/promotion/shipping/tax), fraud gate; runs the Temporal checkout workflow"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    testImplementation(libs.temporal.testing)
}
