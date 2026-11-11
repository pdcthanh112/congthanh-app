plugins {
    id("congthanh.spring-native")
}

description = "Promotion service (hexagonal): vouchers, discount quotes and redemptions for checkout"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
}
