plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api("org.xerial:sqlite-jdbc:3.46.0.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}