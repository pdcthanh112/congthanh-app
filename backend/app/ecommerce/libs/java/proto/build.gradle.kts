plugins {
    `java-library`
    id("congthanh.java-conventions")
    id("congthanh.protobuf-conventions")
}

description = "Java stubs generated from the shared gRPC contracts in backend/app/ecommerce/api/proto"

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("api/proto"))
        }
    }
}

dependencies {
    api(libs.protobuf.java)
    api(libs.protobuf.java.util)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    compileOnly(libs.javax.annotation.api)
}
