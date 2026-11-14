plugins {
    id("congthanh.spring-service")
}

description = "Dispute service (hexagonal): card chargebacks, automatic evidence from order/shipping/refund facts, deadlines"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation(libs.stripe.java)
}
