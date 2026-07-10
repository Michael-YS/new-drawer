package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract tests for `/move-image`. Exercises the full end-to-end path:
 * HTTP route -> FileOps.atomicMove -> NioFileSystemGateway -> real disk
 * -> photos table update -> undoStack push.
 *
 * Each test seeds a small fake source folder + root + target folder on
 * a JUnit temp dir, then POSTs and verifies the file landed in the
 * right place plus the DB state.
 */
class MoveImageRouteTest {

    @Test
    fun `POST move-image relocates the file under the target folder and updates the photo row`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        seedSourceFolderWithPhotos(ctx, tmp, "src1", listOf("a.jpg" to "data-a"))
        seedRootAndFolder(ctx, tmp, "root1", "anime")
        val photoId = 1L

        application { configureServer(ctx) }
        val response = client.post("/move-image") {
            contentType(ContentType.Application.Json)
            setBody("""{"photoId":$photoId,"rootDirId":1,"destinationFolderName":"anime"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertEquals(true, body.contains("\"success\":true"))

        assertTrue(Files.exists(tmp.resolve("root1/anime/a.jpg")))
        assertTrue(!Files.exists(tmp.resolve("src1/a.jpg")))

        val status = Rows.getPhoto(ctx.db, photoId)!!.status
        assertEquals("done", status)
    }

    @Test
    fun `POST move-image pushes an undo record so the next call to can-undo returns true`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        seedSourceFolderWithPhotos(ctx, tmp, "src2", listOf("b.jpg" to "data-b"))
        seedRootAndFolder(ctx, tmp, "root2", "anime")

        application { configureServer(ctx) }
        client.post("/move-image") {
            contentType(ContentType.Application.Json)
            setBody("""{"photoId":1,"rootDirId":1,"destinationFolderName":"anime"}""")
        }

        assertEquals(1, ctx.undoStack.size)
        val canUndo = client.get("/can-undo").bodyAsText()
        assertEquals(true, canUndo.contains("\"canUndo\":true"))
    }

    @Test
    fun `POST undo-last-move restores the file to its original location`(@TempDir tmp: Path) = testApplication {
        val ctx = newTestContext(tmp)
        seedSourceFolderWithPhotos(ctx, tmp, "src3", listOf("c.jpg" to "data-c"))
        seedRootAndFolder(ctx, tmp, "root3", "anime")

        application { configureServer(ctx) }
        client.post("/move-image") {
            contentType(ContentType.Application.Json)
            setBody("""{"photoId":1,"rootDirId":1,"destinationFolderName":"anime"}""")
        }
        client.post("/undo-last-move")

        assertTrue(Files.exists(tmp.resolve("src3/c.jpg")))
        assertTrue(!Files.exists(tmp.resolve("root3/anime/c.jpg")))
        assertEquals("pending", Rows.getPhoto(ctx.db, 1)!!.status)
    }

    private fun seedSourceFolderWithPhotos(ctx: ServerContext, tmp: Path, name: String, files: List<Pair<String, String>>) {
        val srcDir = tmp.resolve(name)
        Files.createDirectory(srcDir)
        for ((fileName, content) in files) {
            Files.writeString(srcDir.resolve(fileName), content)
        }
        val srcFolderId = Rows.insertSourceFolder(ctx.db, srcDir.toString(), name, true, System.currentTimeMillis())
        for ((fileName, _) in files) {
            val photoId = Rows.insertPhoto(ctx.db, srcFolderId, srcDir.resolve(fileName).toString())
            assertEquals(true, photoId > 0)
        }
    }

    private fun seedRootAndFolder(ctx: ServerContext, tmp: Path, rootName: String, folderName: String) {
        val rootDir = tmp.resolve(rootName)
        Files.createDirectory(rootDir)
        Files.createDirectory(rootDir.resolve(folderName))
        val rootId = Rows.insertTargetRootDir(ctx.db, rootDir.toString(), rootName, true, System.currentTimeMillis())
        Rows.insertTargetFolder(ctx.db, rootId, folderName, folderName, 0)
    }

    private fun newTestContext(tmp: Path): ServerContext {
        val db = AppDatabase(":memory:").apply { open() }
        val gateway = NioFileSystemGateway()
        return ServerContext(db, FileOps(gateway), gateway)
    }
}