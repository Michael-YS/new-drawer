package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
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
 * Contract tests for the `/source-folders` route group. Each test spins
 * up a fresh [ServerContext] backed by `:memory:` SQLite and a temp
 * directory so they are independent and can run in any order.
 */
class SourceFoldersRouteTest {

    @Test
    fun `GET source-folders returns empty list when DB has none`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        application { configureServer(ctx) }

        val response = client.get("/source-folders")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST source-folders creates a folder and returns the created row`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        application { configureServer(ctx) }

        val response = client.post("/source-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"C:/photos","displayName":"Photos","recursive":true}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"path\":\"C:/photos\""))
        assertEquals(true, body.contains("\"displayName\":\"Photos\""))
        assertEquals(true, body.contains("\"enabled\":true"))
    }

    @Test
    fun `GET source-folders returns the created folder after a POST`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        application { configureServer(ctx) }

        client.post("/source-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"C:/photos","displayName":"Photos","recursive":false}""")
        }

        val response = client.get("/source-folders")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"recursive\":false"))
    }

    @Test
    fun `DELETE source-folders by id removes it`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        application { configureServer(ctx) }

        client.post("/source-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"C:/a","displayName":"A","recursive":true}""")
        }

        val response = client.delete("/source-folders/1")
        assertEquals(HttpStatusCode.NoContent, response.status)

        val after = client.get("/source-folders")
        assertEquals("[]", after.bodyAsText())
    }

    @Test
    fun `DELETE source-folders with unknown id returns 404`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        application { configureServer(ctx) }

        val response = client.delete("/source-folders/999")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        val fileOps = FileOps(gateway)
        return ServerContext(db, fileOps, gateway)
    }
}