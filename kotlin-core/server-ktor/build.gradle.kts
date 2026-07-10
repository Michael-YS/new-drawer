plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-scanner"))
    implementation(project(":core-fileops"))
    implementation(project(":core-db"))

    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation("io.ktor:ktor-client-core:3.0.3")
    testImplementation("io.ktor:ktor-client-cio:3.0.3")
    testImplementation(testFixtures(project(":core-scanner")))
}

application {
    mainClass.set("com.drawer.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
}