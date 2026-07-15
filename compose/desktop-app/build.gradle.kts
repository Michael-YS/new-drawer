plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":compose-ui"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.drawer.v2.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "Drawer-v2"
            packageVersion = "0.1.0"
        }
    }
}
