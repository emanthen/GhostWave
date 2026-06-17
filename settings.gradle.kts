pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For libsignal-android
        maven { url = uri("https://maven.signal.org/") }
        // For WebRTC SDK
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GhostWave"
include(":app")
