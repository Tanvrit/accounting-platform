plugins {
    kotlin("multiplatform") version "2.4.20"
    id("org.jetbrains.compose") version "1.6.0"
    id("org.jetbrains.compose.compiler") version "1.5.10"
    id("com.android.application") version "9.4.1"
    id("com.android.library") version "9.4.1"
    id("com.squareup.sqldelight") version "2.0.2"
    id("kotlinx-serialization") version "1.11.0"
}

kotlin {
    jvm()

    androidTarget()

    iosArm64()

    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    js {
        browser()
    }
}

compose.desktop.application {
    mainClass = "MainKt"
}