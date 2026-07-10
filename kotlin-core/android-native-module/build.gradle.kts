plugins {
    id("com.android.library")
    kotlin("android")
}

apply(plugin = "org.jetbrains.kotlin.android")

android {
    namespace = "com.drawer.android.saf"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":core-scanner"))
    implementation(project(":core-fileops"))
    implementation(project(":core-db"))

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation(kotlin("test"))
}