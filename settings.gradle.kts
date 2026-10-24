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
        // QuickJS for extensions that evaluate JavaScript; only that artifact is taken from JitPack.
        exclusiveContent {
            forRepository { maven("https://www.jitpack.io") }
            filter { includeGroup("com.github.zhanghai.quickjs-java") }
        }
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
include(":updates")

include(":source:api")
include(":source:local")
include(":source:mangadex")
include(":source:komga")
include(":source:webtoons")
include(":source:naver")
include(":source:remanga")
include(":source:asura")
include(":extension:lib")
include(":extension:manager")
include(":feature:extensions")

include(":translation:api")
include(":translation:core")
include(":translation:mlkit")
include(":translation:gemini")
include(":translation:mangaocr")
include(":translation:openai")
include(":translation:deepl")

include(":feature:library")
include(":feature:updates")
include(":feature:browse")
include(":feature:details")
include(":feature:reader")
include(":feature:history")
include(":feature:settings")
