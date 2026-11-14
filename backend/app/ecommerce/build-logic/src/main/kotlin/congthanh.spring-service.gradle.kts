import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("congthanh.java-conventions")
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(project(":libs:java:common-core"))
    implementation(project(":libs:java:platform"))
    implementation(project(":libs:java:proto"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation(libs.resilience4j.spring.boot4)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.logstash.logback.encoder)
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation(project(":libs:java:testing"))
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("integration") }
}
tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged 'integration' (requires Docker for Testcontainers)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter("test")
}

jib {
    from {
        image = "eclipse-temurin:${libs.versions.jdk.get()}-jre"
        platforms {
            platform { architecture = "amd64"; os = "linux" }
            platform { architecture = "arm64"; os = "linux" }
        }
    }
    to {
        image = (System.getenv("IMAGE_REGISTRY") ?: "ghcr.io/pdcthanh112/congthanhapp") + "/" + project.name
        tags = setOf(System.getenv("IMAGE_TAG") ?: "dev")
    }
    container {
        user = "65532:65532" 
        jvmFlags = listOf(
            "-XX:MaxRAMPercentage=75",
            "-XX:+UseZGC",
            "-XX:+ExitOnOutOfMemoryError",
            "-Djava.security.egd=file:/dev/./urandom",
        )
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels = mapOf(
            "org.opencontainers.image.source" to "https://github.com/pdcthanh112/CongThanhApp",
            "org.opencontainers.image.title" to project.name,
        )
    }
}
