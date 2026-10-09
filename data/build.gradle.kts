// :data — Android library: on-device storage (Room 3 on the phone's own SQLite), the bridge between stored history and the
// engine, backup/export/restore and the step counter (Phase 2 section 13 module plan; Phase 3 Part 4).
// The core packages (com.personalfitnesscoach.data.core.*) are plain Kotlin with no Android types, so they also compile and
// run in tools/local_build.sh; Room and Android code live in com.personalfitnesscoach.data.room and .android.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.personalfitnesscoach.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Room's schema JSON for every database version is committed (Phase 2 data rule 1) and used by migration tests.
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":engine"))
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.framework)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room3.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
