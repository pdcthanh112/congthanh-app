plugins {
    id("congthanh.spring-native")
}

description = "Product service (hexagonal): products, variants, attributes, images; Mongo read model"

dependencies {
    implementation(platform(libs.aws.sdk.bom))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation(libs.aws.sdk.s3)
    implementation(libs.commons.collections4)
    implementation(libs.modelmapper)

    testImplementation("org.springframework.graphql:spring-graphql-test")
}
