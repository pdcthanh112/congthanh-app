import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
}

val libs = the<LibrariesForLibs>()

group = "com.congthanh"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get())
    }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    annotationProcessor(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))
    testAnnotationProcessor(platform(libs.spring.boot.bom))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
