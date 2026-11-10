plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.protobuf.gradle.plugin)
    implementation(libs.jib.gradle.plugin)
    // Lets precompiled script plugins use the version catalog
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
