package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test for the Ktor server bootstrap. Asserts that the health
 * route returns 200 with a known body so future routes have a working
 * baseline to extend.
 */
class HealthRouteTest {

    @Test
    fun `GET health returns 200 ok`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        return ServerContext(db, FileOps(gateway), gateway)
    }
}