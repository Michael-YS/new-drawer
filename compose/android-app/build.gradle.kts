plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.github.Michael_YS.Drawer.v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.Michael_YS.Drawer.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":compose-ui"))
    implementation("androidx.activity:activity-compose:1.9.3")
}
