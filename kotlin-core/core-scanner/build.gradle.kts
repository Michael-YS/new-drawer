plugins {
    kotlin("jvm") version "2.0.21"
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    useJUnitPlatform()
}