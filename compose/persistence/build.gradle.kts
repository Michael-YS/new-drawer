plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation("app.cash.sqldelight:runtime:2.0.2")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
        }
        getByName("desktopMain").dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}

android {
    namespace = "com.drawer.v2.persistence"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

sqldelight {
    databases {
        create("DrawerDatabase") {
            packageName.set("com.drawer.v2.persistence")
        }
    }
}
