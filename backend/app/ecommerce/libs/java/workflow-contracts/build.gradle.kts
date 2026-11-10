plugins {
    `java-library`
    id("congthanh.java-conventions")
}

description = "Temporal contracts shared between workers: task queues, activity interfaces, signals and their payloads"

dependencies {
    api(platform(libs.temporal.bom))
    api(libs.temporal.sdk)
}
