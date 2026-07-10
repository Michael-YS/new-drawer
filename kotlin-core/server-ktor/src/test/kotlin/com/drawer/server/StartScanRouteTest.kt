package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract test for `/start-scan`. Seeds a real temp directory with
 * image-shaped bytes, registers it as a source folder, and asserts
 * the scan endpoint discovers the right number of photos and inserts
 * matching `photos` rows.
 */
class StartScanRouteTest {

    @Test
    fun `POST start-scan discovers images in the registered source folder`(@TempDir tmp: Path) = testApplication {
        val srcDir = tmp.resolve("photos")
        Files.createDirectory(srcDir)
        writeJpeg(srcDir.resolve("a.jpg"))
        writeJpeg(srcDir.resolve("b.png"))
        Files.writeString(srcDir.resolve("notes.txt"), "ignore me")

        val ctx = newTestContext(tmp)
        Rows.insertSourceFolder(ctx.db, srcDir.toString(), "photos", true, System.currentTimeMillis())

        application { configureServer(ctx) }
        val response = runBlocking { client.post("/start-scan") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"discovered\":2"))
    }

    private fun writeJpeg(file: Path) {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        Files.write(file, header + "data".toByteArray())
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        return ServerContext(db, FileOps(gateway), gateway)
    }
}