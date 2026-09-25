// Root build.gradle.kts for the accounting platform app
plugins {
    // These apply to subprojects
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.serialization) apply false
}

allprojects {
    group = "com.tanvrit.accounting.platform"
    version = "1.0.0"
}
