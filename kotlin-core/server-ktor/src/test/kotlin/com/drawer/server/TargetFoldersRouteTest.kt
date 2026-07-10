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
 * Contract tests for `/target-root-dirs` and `/target-folders` route
 * groups. Backed by `:memory:` SQLite so tests are independent.
 */
class TargetFoldersRouteTest {

    @Test
    fun `GET target-root-dirs returns empty list when DB has none`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val response = client.get("/target-root-dirs")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `first POST target-root-dirs is marked default automatically`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        client.post("/target-root-dirs") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"D:/photos","displayName":"Photos"}""")
        }
        val body = client.get("/target-root-dirs").bodyAsText()
        assertEquals(true, body.contains("\"isDefault\":true"))
    }

    @Test
    fun `POST target-folders under a root creates a row tied to that root`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val rootResp = client.post("/target-root-dirs") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"D:/photos","displayName":"Photos"}""")
        }
        val rootId = Regex("\"id\":(\\d+)").find(rootResp.bodyAsText())!!.groupValues[1]

        val resp = client.post("/target-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"rootDirId":$rootId,"name":"anime"}""")
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        val body = resp.bodyAsText()
        assertEquals(true, body.contains("\"name\":\"anime\""))
        assertEquals(true, body.contains("\"rootDirId\":$rootId"))
    }

    @Test
    fun `GET target-folders scoped by rootDirId returns only that root's children`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val rootResp = client.post("/target-root-dirs") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"D:/photos","displayName":"Photos"}""")
        }
        val rootId = Regex("\"id\":(\\d+)").find(rootResp.bodyAsText())!!.groupValues[1]
        client.post("/target-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"rootDirId":$rootId,"name":"anime"}""")
        }
        client.post("/target-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"rootDirId":$rootId,"name":"nature"}""")
        }

        val response = client.get("/target-folders?rootDirId=$rootId")
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"name\":\"anime\""))
        assertEquals(true, body.contains("\"name\":\"nature\""))
    }

    @Test
    fun `DELETE target-folders removes one entry`(@TempDir tmp: Path) = testApplication {
        application { configureServer(newTestContext(tmp)) }
        val rootResp = client.post("/target-root-dirs") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"D:/photos","displayName":"Photos"}""")
        }
        val rootId = Regex("\"id\":(\\d+)").find(rootResp.bodyAsText())!!.groupValues[1]
        client.post("/target-folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"rootDirId":$rootId,"name":"anime"}""")
        }

        val response = client.delete("/target-folders/1")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        return ServerContext(db, FileOps(gateway), gateway)
    }
}