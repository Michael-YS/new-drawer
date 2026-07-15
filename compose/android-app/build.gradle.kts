plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersion = providers.environmentVariable("DRAWER_RELEASE_VERSION").orElse("0.1.0").get()
val releaseVersionCode = providers.environmentVariable("DRAWER_RELEASE_VERSION_CODE").orElse("1").get().toInt()
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull

android {
    namespace = "com.github.Michael_YS.Drawer.v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.Michael_YS.Drawer.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersion
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":compose-ui"))
    implementation(project(":persistence"))
    implementation(project(":scanner"))
    implementation(project(":storage-contract"))
    implementation(project(":storage-saf"))
    implementation(project(":storage-transaction"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
