plugins {
    id("congthanh.spring-service")
}

description = "Payment service (hexagonal): authorize/capture/refund as Temporal activities; Stripe, PayPal, VNPay, COD"

dependencies {
    implementation(project(":libs:java:workflow-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation(libs.stripe.java)

    testImplementation(libs.temporal.testing)
}
