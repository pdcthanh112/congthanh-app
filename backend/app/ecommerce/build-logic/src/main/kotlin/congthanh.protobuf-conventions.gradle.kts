import com.google.protobuf.gradle.id
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.google.protobuf")
}

val libs = the<LibrariesForLibs>()

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpcJava.get()}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}
