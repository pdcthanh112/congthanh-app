plugins {
    id("congthanh.spring-service")
}

description = "Subscription service (hexagonal): Subscribe & Save plans, skip/pause/cancel, renewal schedule and dunning"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
}
