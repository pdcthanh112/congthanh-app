// Bổ sung cho congthanh.spring-service: đóng thêm bản GraalVM native image (Spring AOT).
// Khởi động ~0.1s, RAM thấp hơn JVM nhiều: hợp với service scale theo đợt (KEDA, flash sale).
//   ./gradlew :services:<svc>:processAot      kiểm AOT (không cần GraalVM, CI chạy mọi PR)
//   ./gradlew :services:<svc>:nativeCompile   build binary (cần GraalVM 25)
//   ./gradlew :services:<svc>:bootBuildImage  image native qua Paketo buildpacks (cần Docker)
// Image JVM (Jib) vẫn là mặc định; Helm chọn image native bằng image.variant: native (tag <sha>-native).
plugins {
    id("congthanh.spring-service")
    id("org.graalvm.buildtools.native")
}

graalvmNative {
    // Metadata reachability cộng đồng (Netty, Postgres driver, Liquibase...) tải theo phiên bản thư viện.
    metadataRepository { enabled = true }
    binaries {
        named("main") {
            imageName = project.name
            buildArgs.addAll(
                "--no-fallback",
                "-march=compatibility", // chạy được trên mọi CPU x86-64/arm64 của node Karpenter
                "-H:+ReportExceptionStackTraces",
            )
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName = (System.getenv("IMAGE_REGISTRY") ?: "ghcr.io/pdcthanh112/congthanhapp") + "/" + project.name +
        ":" + (System.getenv("IMAGE_TAG") ?: "dev") + "-native"
    environment.put("BP_JVM_VERSION", "25")
    // Cùng tham số với nativeCompile để binary local và binary trong image giống nhau.
    environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "--no-fallback -march=compatibility")
    // CI đẩy thẳng lên registry (--publishImage); local chỉ nạp vào Docker daemon.
    docker {
        publishRegistry {
            username = System.getenv("REGISTRY_USERNAME") ?: ""
            password = System.getenv("REGISTRY_PASSWORD") ?: ""
        }
    }
}
