package com.drawer.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Configures the Ktor routing tree for the photo-organizer backend.
 *
 * Extracted as a top-level function so tests can call it inside
 * `testApplication { application { configureServer() } }` without
 * booting a real Netty engine.
 */
fun Application.configureServer() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
    }
}

/**
 * Entrypoint for the packaged Windows backend. Reads the listen port
 * from the `PORT` env var (default 8080) and starts Netty.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) {
        configureServer()
    }.start(wait = true)
}