plugins {
    id("congthanh.spring-native")
}

description = "Product service (hexagonal): products, variants, attributes, images; Mongo read model (CQRS) built from Kafka events"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation(libs.commons.collections4)
    implementation(libs.modelmapper)

    testImplementation("org.springframework.graphql:spring-graphql-test")
}
