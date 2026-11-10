plugins {
    `java-library`
    id("congthanh.java-conventions")
}

description = "Shared test helpers: ArchUnit rules for the hexagonal layout"

dependencies {
    api(libs.archunit.junit5)
}
