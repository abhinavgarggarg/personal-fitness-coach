// :engine — pure Kotlin, no Android dependency. Deterministic training rules (Phase 1 Rule Registry v1.0.1).
plugins {
    alias(libs.plugins.kotlin.jvm)
    // NFR-13 line coverage of the engine (Gradle's own JaCoCo plugin at its default version; Part 8, D-108).
    jacoco
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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(false)
        csv.required.set(false)
    }
}
