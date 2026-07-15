plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}

allprojects {
    group = "com.drawer.v2"
    version = "0.1.0-SNAPSHOT"
}
