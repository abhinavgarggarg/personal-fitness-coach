// Root build: plugin versions come only from gradle/libs.versions.toml (Phase 2 supply-chain rule).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
