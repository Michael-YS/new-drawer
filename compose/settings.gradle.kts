pluginManagement {
    repositories {
        google()
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

rootProject.name = "drawer-v2"

include(":domain")
include(":storage-contract")
include(":scanner")
include(":storage-transaction")
include(":persistence")
include(":compose-ui")
include(":storage-nio")
include(":storage-saf")
include(":android-app")
include(":desktop-app")
