package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for `/settings`. Defaults come from the merged-refactor
 * spec: showSkipped=false, downscaleHighRes=true, recursiveScanDefault=true.
 */
class SettingsRouteTest {

    @Test
    fun `GET settings returns documented defaults on a fresh database`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val body = client.get("/settings").bodyAsText()
        assertEquals(true, body.contains("\"showSkipped\":false"))
        assertEquals(true, body.contains("\"downscaleHighRes\":true"))
        assertEquals(true, body.contains("\"recursiveScanDefault\":true"))
    }

    @Test
    fun `PUT settings persists each supplied field and leaves the rest alone`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }

        client.put("/settings") {
            contentType(ContentType.Application.Json)
            setBody("""{"showSkipped":true}""")
        }
        val body = client.get("/settings").bodyAsText()
        assertEquals(true, body.contains("\"showSkipped\":true"))
        assertEquals(true, body.contains("\"downscaleHighRes\":true"))
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        return ServerContext(db, FileOps(gateway), gateway)
    }
}