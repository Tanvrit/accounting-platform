rootProject.name = "accounting"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Tanvrit SDK + core — Cloudflare Worker proxy (R2-first), no credentials.
        maven { url = uri("https://maven.tanvrit.com") }
        // mavenLocal LAST (supply-chain hygiene): remote/pinned coordinates win;
        // mavenLocal only resolves versions the remotes don't have, so a stale or
        // poisoned local artifact can never shadow a genuinely published one.
        mavenLocal()
    }
}

include(":composeApp")
