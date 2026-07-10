rootProject.name = "kotlin-core"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":core-scanner")
include(":core-fileops")
include(":core-db")
include(":server-ktor")
include(":android-native-module")