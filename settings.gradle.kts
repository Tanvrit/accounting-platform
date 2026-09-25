pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.tanvrit.com")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.tanvrit.com")
        mavenLocal()
    }
}

rootProject.name = "accounting"

include(":composeApp")
// Add other modules as needed
