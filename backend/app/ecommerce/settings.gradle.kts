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

include(":libs:java:common-core")
include(":libs:java:proto")
include(":libs:java:platform")
include(":libs:java:workflow-contracts")
include(":libs:java:testing")

include(":services:product-service")
include(":services:order-service")
include(":services:payment-service")
include(":services:promotion-service")
include(":services:checkout-service")
include(":services:ledger-service")
include(":services:returns-service")
include(":services:stored-value-service")
include(":services:dispute-service")
include(":services:loyalty-service")
include(":services:subscription-service")

include(":data:streaming:stream-processor")
