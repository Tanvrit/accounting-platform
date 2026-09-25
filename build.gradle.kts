// Root build file for the Tanvrit Accounting platform app.
// All versions live in gradle/libs.versions.toml — this file only applies
// plugins (lazily) and configures the repo-wide ktlint gate.

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "com.tanvrit.accounting.platform"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // ktlint ENGINE pin — fleet-wide constant 1.5.0 (sdk/ and server/ pin the
        // same engine). The Gradle plugin version lives in
        // gradle/libs.versions.toml; the engine does not move with it.
        version.set("1.5.0")
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        // Generated Compose resource code + all build output is not lintable.
        filter {
            exclude { entry ->
                val path = entry.file.absolutePath
                path.contains("/build/") || path.contains("\\build\\") || path.contains("/generated/")
            }
        }
    }
}
