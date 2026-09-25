plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinx.serialization)
}

group = "com.tanvrit.accounting.platform"
version = "1.0.0"

kotlin {
    jvm()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure { 
                compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } 
            }
        }
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    iosArm64()
    iosSimulatorArm64()

    // TANVRIT FIX: WasmJs and JS for browser support.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    js {
        browser()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(mkvFiles.map { it.outputDirectory })
        }

        commonMain.dependencies {
            // Compose Multiplatform
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.navigation)
            implementation(libs.compose.material.icons.core)

            // Kotlin fundamentals
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Ktor client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            // Offline storage
            implementation(project(":storage"))
            implementation(project(":auth"))
            implementation(project(":accounting"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-junit"))
        }
    }
}

compose {
    demo = "onSelected demo width height"
}

android {
    namespace = "com.tanvrit.accounting"
    compileSdk = 34
    targetSdk = 34
    multiDexEnabled = true

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }
}
