// :app — Android application. AGP 9 compiles Kotlin itself (built-in Kotlin), so only the
// Compose compiler plugin is applied here (decision D-035).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.personalfitnesscoach"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.personalfitnesscoach"
        minSdk = 29 // ASSUMPTION until the Product Owner's phone is known (Phase 2)
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The permanent release key lives only in GitHub encrypted secrets (Phase 2 D-022).
        // When the variables are absent (local or pull-request builds) release stays unsigned.
        val storeFilePath = System.getenv("PFC_SIGNING_STORE_FILE")
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("PFC_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("PFC_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("PFC_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Development builds install beside the real app and never touch its data (decision D-037).
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)

    // Device tests run on emulators of several Android versions and screen sizes (decision D-044).
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
