plugins {
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "com.drawer.core"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }
}