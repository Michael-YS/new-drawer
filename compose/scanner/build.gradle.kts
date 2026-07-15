plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":storage-contract"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}

android {
    namespace = "com.drawer.v2.scanner"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
