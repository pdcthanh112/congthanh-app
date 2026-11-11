plugins {
    id("congthanh.spring-native")
}

description = "Payment service (hexagonal): authorize/capture/refund as Temporal activities; Stripe (cards, Apple Pay, Google Pay), PayPal"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation(libs.stripe.java)

    testImplementation(libs.temporal.testing)
}
