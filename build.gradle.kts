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
        version.set("1.8.0")
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

// Ktor's Node transport metadata still requests ws 8.20.1. Use the compatible
// 8.x security patch for both JS and Wasm dependency graphs (GHSA-96hv-2xvq-fx4p).
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
        resolution("ws", "8.21.0")
    }
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension> {
        resolution("ws", "8.21.0")
    }
}

// Mocha 12 removes vulnerable serializer/diff dependencies and supports the managed Node runtimes.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
        versions.mocha.version = "12.0.3"
        versions.kotlinWebHelpers.version = "3.5.4"
    }
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension> {
        versions.mocha.version = "12.0.3"
        versions.kotlinWebHelpers.version = "3.5.4"
    }
}
