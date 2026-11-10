pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "congthanhapp-ecommerce"

// Thư viện Java dùng chung (libs/java/*)
include(":libs:java:common-core")
include(":libs:java:proto")
include(":libs:java:platform")
include(":libs:java:workflow-contracts")
include(":libs:java:testing")

// Service Spring Boot (services/*)
include(":services:product-service")
include(":services:order-service")
include(":services:payment-service")
include(":services:promotion-service")
