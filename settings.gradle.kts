pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "Tsunayomi"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:image")
include(":core:network")

include(":data")
include(":downloads")

include(":source:api")
include(":source:local")
include(":source:mangadex")
include(":source:komga")

include(":translation:api")
include(":translation:core")
include(":translation:mlkit")
include(":translation:gemini")

include(":feature:library")
include(":feature:browse")
include(":feature:details")
include(":feature:reader")
include(":feature:history")
include(":feature:settings")
