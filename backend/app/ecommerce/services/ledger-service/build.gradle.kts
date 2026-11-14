plugins {
    id("congthanh.spring-service")
}

description = "Ledger service (hexagonal): double-entry ledger, posting rules from payment events, balances, trial balance"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
}
