plugins {
    id("congthanh.java-conventions")
    id("congthanh.protobuf-conventions")
}

description = "Flink stream processor: customer risk features, product trending, stock cover (Kafka -> Kafka, protobuf EventEnvelope)"

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Image chạy flink:2.2-java21 nên biên dịch bytecode Java 21 (toolchain vẫn là JDK 25 của repo).
// Vì vậy không dùng :libs:java:proto (bytecode 25) mà tự sinh lại từ chính api/proto ở mức Java 21.
sourceSets {
    main {
        proto {
            srcDir(rootProject.file("api/proto"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

// Flink runtime có sẵn trong image (/opt/flink/lib) → chỉ compileOnly; connector + proto đóng vào usrlib.
val flinkProvided: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(flinkProvided) }
configurations.testImplementation { extendsFrom(flinkProvided) }

dependencies {
    flinkProvided(libs.flink.streaming.java)
    flinkProvided(libs.flink.clients)

    implementation(libs.flink.connector.base)
    implementation(libs.flink.connector.kafka)
    implementation(libs.protobuf.java)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    compileOnly(libs.javax.annotation.api)

    testImplementation(libs.flink.test.utils)
}

tasks.jar {
    archiveFileName = "stream-processor.jar"
}

// build/flink/usrlib = jar job + thư viện không có sẵn trong Flink; Dockerfile chép nguyên thư mục này.
val flinkUsrlib by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(configurations.runtimeClasspath) {
        exclude("flink-core-*", "flink-runtime-*", "flink-streaming-java-*", "flink-clients-*", "flink-shaded-*")
        exclude("slf4j-api-*", "log4j-*")
    }
    into(layout.buildDirectory.dir("flink/usrlib"))
}

tasks.assemble { dependsOn(flinkUsrlib) }
