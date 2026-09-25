// Root project build file for the accounting platform app.
// Subproject settings and dependencies live in the subproject's own build.gradle.kts.

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
}

allprojects {
    group = "com.tanvrit.accounting.platform"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://maven.tanvrit.com")
    }
}
