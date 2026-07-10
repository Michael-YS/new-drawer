plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-scanner"))

    testImplementation(testFixtures(project(":core-scanner")))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    useJUnitPlatform()
}