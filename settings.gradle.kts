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
    }
}

rootProject.name = "OyeSuno"

include(":app")
include(":core")
include(":core-ui")
include(":core-audio")
include(":core-database")
include(":core-storage")
include(":core-permissions")
include(":feature-home")
include(":feature-recorder")
include(":feature-player")
include(":feature-library")
include(":feature-editor")
include(":feature-wakeword")
include(":feature-scheduler")
include(":feature-settings")
