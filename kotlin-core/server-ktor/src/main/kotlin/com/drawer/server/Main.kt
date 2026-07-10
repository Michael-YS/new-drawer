package com.drawer.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import com.drawer.core.db.AppDatabase

/**
 * Configures the Ktor routing tree for the photo-organizer backend.
 *
 * Extracted as a top-level function so tests can call it inside
 * `testApplication { application { configureServer(ctx) } }` without
 * booting a real Netty engine.
 */
fun Application.configureServer(ctx: ServerContext) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }

        sourceFoldersRoutes(ctx)
        targetRootDirsRoutes(ctx)
        targetFoldersRoutes(ctx)
        settingsRoutes(ctx)
        photosRoutes(ctx)
        moveRoutes(ctx)
        scanRoutes(ctx)
        undoRoutes(ctx)
    }
}

/**
 * Entrypoint for the packaged Windows backend. Reads the listen port
 * from the `PORT` env var (default 8080) and starts Netty.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val ctx = ServerContext.forProduction(port)
    embeddedServer(Netty, port = port) {
        configureServer(ctx)
    }.start(wait = true)
}

// region source-folders

private fun io.ktor.server.routing.Route.sourceFoldersRoutes(ctx: ServerContext) {
    get("/source-folders") {
        call.respond(Rows.listSourceFolders(ctx.db))
    }
    post("/source-folders") {
        val req = call.receive<CreateSourceFolderRequest>()
        val id = Rows.insertSourceFolder(
            ctx.db,
            path = req.path,
            displayName = req.displayName,
            recursive = req.recursive,
            addedAt = System.currentTimeMillis(),
        )
        val row = Rows.findSourceFolder(ctx.db, id)
        call.respond(HttpStatusCode.Created, row!!)
    }
    delete("/source-folders/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        if (Rows.deleteSourceFolder(ctx.db, id)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

// endregion

// region target-root-dirs

private fun io.ktor.server.routing.Route.targetRootDirsRoutes(ctx: ServerContext) {
    get("/target-root-dirs") {
        call.respond(Rows.listTargetRootDirs(ctx.db))
    }
    post("/target-root-dirs") {
        val req = call.receive<CreateTargetRootDirRequest>()
        val id = Rows.insertTargetRootDir(
            ctx.db,
            path = req.path,
            displayName = req.displayName,
            isDefault = Rows.listTargetRootDirs(ctx.db).isEmpty(),
            addedAt = System.currentTimeMillis(),
        )
        call.respond(HttpStatusCode.Created, Rows.listTargetRootDirs(ctx.db).single { it.id == id })
    }
    delete("/target-root-dirs/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        if (Rows.deleteTargetRootDir(ctx.db, id)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

// endregion

// region target-folders

private fun io.ktor.server.routing.Route.targetFoldersRoutes(ctx: ServerContext) {
    get("/target-folders") {
        val rootId = call.request.queryParameters["rootDirId"]?.toLongOrNull()
        call.respond(Rows.listTargetFolders(ctx.db, rootId))
    }
    post("/target-folders") {
        val req = call.receive<CreateTargetFolderRequest>()
        val nextOrder = Rows.listTargetFolders(ctx.db, req.rootDirId).size
        val id = Rows.insertTargetFolder(
            ctx.db,
            rootDirId = req.rootDirId,
            name = req.name,
            displayName = req.name,
            sortOrder = nextOrder,
        )
        call.respond(HttpStatusCode.Created, Rows.listTargetFolders(ctx.db, req.rootDirId).single { it.id == id })
    }
    delete("/target-folders/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        if (Rows.deleteTargetFolder(ctx.db, id)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

// endregion

// region settings

private fun io.ktor.server.routing.Route.settingsRoutes(ctx: ServerContext) {
    get("/settings") {
        call.respond(loadSettings(ctx.db))
    }
    put("/settings") {
        val req = call.receive<UpdateSettingsRequest>()
        val current = loadSettings(ctx.db)
        val merged = current.copy(
            showSkipped = req.showSkipped ?: current.showSkipped,
            downscaleHighRes = req.downscaleHighRes ?: current.downscaleHighRes,
            recursiveScanDefault = req.recursiveScanDefault ?: current.recursiveScanDefault,
        )
        saveSettings(ctx.db, merged)
        call.respond(merged)
    }
}

internal const val SETTING_SHOW_SKIPPED = "showSkipped"
internal const val SETTING_DOWNSCALE_HIGH_RES = "downscaleHighRes"
internal const val SETTING_RECURSIVE_SCAN_DEFAULT = "recursiveScanDefault"

internal fun loadSettings(db: AppDatabase): SettingsDto {
    val all = Rows.getAllSettings(db)
    return SettingsDto(
        showSkipped = all[SETTING_SHOW_SKIPPED]?.toBoolean() ?: false,
        downscaleHighRes = all[SETTING_DOWNSCALE_HIGH_RES]?.toBoolean() ?: true,
        recursiveScanDefault = all[SETTING_RECURSIVE_SCAN_DEFAULT]?.toBoolean() ?: true,
    )
}

internal fun saveSettings(db: AppDatabase, s: SettingsDto) {
    Rows.putSetting(db, SETTING_SHOW_SKIPPED, s.showSkipped.toString())
    Rows.putSetting(db, SETTING_DOWNSCALE_HIGH_RES, s.downscaleHighRes.toString())
    Rows.putSetting(db, SETTING_RECURSIVE_SCAN_DEFAULT, s.recursiveScanDefault.toString())
}

// endregion

// region photos (stubs wired in next commit)

private fun Route.photosRoutes(ctx: ServerContext) {
    get("/photos") {
        call.respond(emptyList<PhotoDto>())
    }
    post("/photos/reset-statuses") {
        call.respond(HttpStatusCode.NoContent)
    }
}

// endregion

// region move / trash / restore / undo (stubs wired in following commits)

private fun Route.moveRoutes(ctx: ServerContext) {
    post("/move-image") {
        call.respond(MoveResultDto(success = false, errorMessage = "not yet wired"))
    }
    post("/trash-image") {
        call.respond(MoveResultDto(success = false, errorMessage = "not yet wired"))
    }
    post("/restore-trashed") {
        call.respond(MoveResultDto(success = false, errorMessage = "not yet wired"))
    }
    post("/undo-last-move") {
        call.respond(MoveResultDto(success = false, errorMessage = "not yet wired"))
    }
}

private fun Route.undoRoutes(ctx: ServerContext) {
    get("/can-undo") {
        call.respond(mapOf("canUndo" to ctx.undoStack.isNotEmpty()))
    }
}

private fun Route.scanRoutes(ctx: ServerContext) {
    post("/start-scan") {
        call.respond(mapOf("scanId" to ""))
    }
    get("/next-image") {
        call.respondText("null", io.ktor.http.ContentType.Application.Json)
    }
    post("/cancel-scan") {
        call.respond(HttpStatusCode.NoContent)
    }
}

// endregion