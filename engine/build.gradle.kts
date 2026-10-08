// :engine — pure Kotlin, no Android dependency. Deterministic training rules (Phase 1 Rule Registry v1.0.1).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
