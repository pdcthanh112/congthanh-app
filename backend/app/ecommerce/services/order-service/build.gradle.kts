plugins {
    id("congthanh.spring-native")
}

description = "Order service (hexagonal): checkout, order lifecycle; runs the checkout saga activities (workflow lives in checkout-service)"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(libs.temporal.testing)
}
