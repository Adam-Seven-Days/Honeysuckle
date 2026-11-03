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

rootProject.name = "honeysuckle"

include(":app")

include(":core:common")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:player")
include(":core:qrcode")
include(":core:danmaku")
include(":core:danmaku:host")

include(":feature:splash")
include(":feature:home")
include(":feature:search")
include(":feature:videodetail")
include(":feature:login")
include(":feature:user")
include(":feature:history")
include(":feature:favorite")
include(":feature:live")
include(":feature:dynamic")
include(":feature:anime")
include(":feature:space")
include(":feature:settings")
include(":feature:message")
