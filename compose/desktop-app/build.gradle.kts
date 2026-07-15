plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersion = providers.gradleProperty("releaseVersion").orElse("0.1.0").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":compose-ui"))
    implementation(project(":persistence"))
    implementation(project(":scanner"))
    implementation(project(":storage-contract"))
    implementation(project(":storage-nio"))
    implementation(project(":storage-transaction"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.drawer.v2.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "Drawer-v2"
            packageVersion = releaseVersion
            windows {
                msiPackageVersion = releaseVersion
            }
        }
    }
}
