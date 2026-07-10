package com.drawer.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test for the Ktor server bootstrap. Asserts that the health
 * route returns 200 with a known body so future routes have a working
 * baseline to extend.
 */
class HealthRouteTest {

    @Test
    fun `GET health returns 200 ok`() = testApplication {
        application {
            configureServer()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }
}