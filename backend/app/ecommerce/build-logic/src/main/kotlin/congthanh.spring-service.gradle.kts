import org.gradle.accessors.dm.LibrariesForLibs

// Convention cho mọi service Spring Boot (hexagonal):
//   web + validation + OAuth2 resource server (Keycloak) + actuator/Prometheus
//   gRPC server/client (Spring gRPC), OpenTelemetry (traces, metrics, logs qua OTLP)
//   Liquibase, Resilience4j, libs/java/platform (outbox, Idempotency-Key, consumer chống trùng)
//   ArchUnit + Testcontainers cho test, Jib để đóng image không cần Dockerfile.
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

// Test cần Docker (Testcontainers) gắn tag "integration"; chạy riêng bằng ./gradlew integrationTest.
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

// Image OCI không cần Dockerfile: ./gradlew :services:<svc>:jib (push) hoặc jibDockerBuild (local).
// Tag và registry lấy từ biến môi trường để CI quyết định (xem .github/workflows/ci-java.yml).
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
        user = "65532:65532" // trùng runAsUser trong Helm chart
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
