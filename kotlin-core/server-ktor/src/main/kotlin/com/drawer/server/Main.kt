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

// region photos

private fun Route.photosRoutes(ctx: ServerContext) {
    get("/photos") {
        val sourceId = call.request.queryParameters["sourceFolderId"]?.toLongOrNull()
        call.respond(Rows.listPhotos(ctx.db, sourceId))
    }
    post("/photos/reset-statuses") {
        Rows.resetPhotoStatuses(ctx.db)
        call.respond(HttpStatusCode.NoContent)
    }
}

// endregion

// region move / trash / restore

private fun Route.moveRoutes(ctx: ServerContext) {
    post("/move-image") {
        val req = call.receive<MoveImageRequest>()
        handleMoveImage(ctx, req, call)
    }
    post("/trash-image") {
        val req = call.receive<MoveImageRequest>()
        handleTrashImage(ctx, req, call)
    }
    post("/restore-trashed") {
        val req = call.receive<MoveImageRequest>()
        handleRestoreTrashed(ctx, req, call)
    }
}

private suspend fun handleMoveImage(ctx: ServerContext, req: MoveImageRequest, call: io.ktor.server.application.ApplicationCall) {
    val photo = Rows.getPhoto(ctx.db, req.photoId)
        ?: return call.respondError("photo not found")
    val entryPath = photo.entryPath
        ?: return call.respondError("photo has no source path")
    val sourceDir = Rows.sourceFolderPath(ctx.db, photo.sourceFolderId)
        ?: return call.respondError("source folder not found")
    val rootPath = ctx.db.connection.prepareStatement(
        "SELECT path FROM target_root_dirs WHERE id = ?",
    ).use { stmt ->
        stmt.setLong(1, req.rootDirId)
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    } ?: return call.respondError("root dir not found")

    val srcHandle = ctx.gateway.entryOf(java.nio.file.Paths.get(entryPath))
    val srcParentHandle = ctx.gateway.dirOf(java.nio.file.Paths.get(sourceDir))
    val dstParentHandle = ctx.gateway.dirOf(java.nio.file.Paths.get(rootPath, req.destinationFolderName))
    val dstName = java.nio.file.Paths.get(entryPath).fileName.toString()

    try {
        ctx.fileOps.atomicMove(srcHandle, dstParentHandle, dstName)
        Rows.markPhotoMoved(ctx.db, req.photoId, java.nio.file.Paths.get(rootPath, req.destinationFolderName, dstName).toString(), System.currentTimeMillis())
        ctx.undoStack.addLast(
            UndoRecord(
                moveId = "move-${req.photoId}-${System.nanoTime()}",
                currentParentPath = java.nio.file.Paths.get(rootPath, req.destinationFolderName).toString(),
                currentName = dstName,
                originalParentPath = sourceDir,
                originalName = dstName,
                photoId = req.photoId,
            ),
        )
        call.respond(MoveResultDto(success = true))
    } catch (e: Exception) {
        call.respond(MoveResultDto(success = false, errorMessage = e.message))
    }
}

private suspend fun handleTrashImage(ctx: ServerContext, req: MoveImageRequest, call: io.ktor.server.application.ApplicationCall) {
    val photo = Rows.getPhoto(ctx.db, req.photoId)
        ?: return call.respondError("photo not found")
    val entryPath = photo.entryPath
        ?: return call.respondError("photo has no source path")
    val rootPath = ctx.db.connection.prepareStatement(
        "SELECT path FROM target_root_dirs WHERE is_default = 1 LIMIT 1",
    ).use { stmt ->
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    } ?: return call.respondError("no default root configured")

    val srcHandle = ctx.gateway.entryOf(java.nio.file.Paths.get(entryPath))
    val trashParent = ctx.gateway.dirOf(java.nio.file.Paths.get(rootPath))
    try {
        ctx.fileOps.trashImage(srcHandle, trashParent)
        Rows.markPhotoTrashed(ctx.db, req.photoId, entryPath, System.currentTimeMillis())
        call.respond(MoveResultDto(success = true))
    } catch (e: Exception) {
        call.respond(MoveResultDto(success = false, errorMessage = e.message))
    }
}

private suspend fun handleRestoreTrashed(ctx: ServerContext, req: MoveImageRequest, call: io.ktor.server.application.ApplicationCall) {
    val photo = Rows.getPhoto(ctx.db, req.photoId)
        ?: return call.respondError("photo not found")
    val originalPath = photo.originalPath
        ?: return call.respondError("photo has no original path")

    val trashedEntry = ctx.gateway.entryOf(java.nio.file.Paths.get(originalPath).parent.parent.resolve(".trash").resolve(java.nio.file.Paths.get(originalPath).fileName.toString() + "__").let { /* placeholder */ java.nio.file.Paths.get(originalPath) })
    // The originalPath stored is the pre-trash path; the trashed file lives under .trash/ with a timestamp suffix.
    // For restore we look up the trashed entry by walking .trash/ and matching filename prefix.
    val trashDir = ctx.gateway.dirOf(java.nio.file.Paths.get(originalPath).parent.parent.resolve(".trash"))
    val matches = ctx.gateway.listChildren(trashDir).filter { it.name.startsWith(java.nio.file.Paths.get(originalPath).fileName.toString() + "__") }
    if (matches.isEmpty()) return call.respondError("trashed file not found")
    val trashed = matches.first()
    val originalParent = ctx.gateway.dirOf(java.nio.file.Paths.get(originalPath).parent)
    val originalName = java.nio.file.Paths.get(originalPath).fileName.toString()
    try {
        ctx.fileOps.restoreTrashed(trashed, originalParent, originalName)
        call.respond(MoveResultDto(success = true))
    } catch (e: Exception) {
        call.respond(MoveResultDto(success = false, errorMessage = e.message))
    }
}

// endregion

// region undo + scan stubs

private fun Route.undoRoutes(ctx: ServerContext) {
    get("/can-undo") {
        call.respond(mapOf("canUndo" to ctx.undoStack.isNotEmpty()))
    }
    post("/undo-last-move") {
        val record = ctx.undoStack.removeLastOrNull()
        if (record == null) {
            call.respond(MoveResultDto(success = false, errorMessage = "nothing to undo"))
            return@post
        }
        try {
            val current = ctx.gateway.entryOf(java.nio.file.Paths.get(record.currentParentPath, record.currentName))
            val origParent = ctx.gateway.dirOf(java.nio.file.Paths.get(record.originalParentPath))
            ctx.fileOps.undoMove(current, origParent, record.originalName)
            Rows.markPhotoUndone(ctx.db, record.photoId)
            call.respond(MoveResultDto(success = true))
        } catch (e: Exception) {
            call.respond(MoveResultDto(success = false, errorMessage = e.message))
        }
    }
}

private fun Route.scanRoutes(ctx: ServerContext) {
    post("/start-scan") {
        handleStartScan(ctx, call)
    }
    get("/next-image") {
        val photo = nextPendingPhoto(ctx)
        if (photo == null) {
            call.respondText("null", io.ktor.http.ContentType.Application.Json)
        } else {
            call.respond(photo)
        }
    }
    post("/cancel-scan") {
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun handleStartScan(ctx: ServerContext, call: io.ktor.server.application.ApplicationCall) {
    val sources = Rows.listSourceFolders(ctx.db).filter { it.enabled }
    val alreadyPending = Rows.listPhotos(ctx.db).count { it.status == "pending" }
    var discovered = 0
    for (sf in sources) {
        val rootDir = ctx.gateway.dirOf(java.nio.file.Paths.get(sf.path))
        com.drawer.core.scanner.scanImagesFlow(rootDir, ctx.gateway).collect { event ->
            if (event is com.drawer.core.scanner.ScanEvent.ImageFound) {
                val path = ctx.gateway.pathOf(event.entry)
                Rows.insertPhoto(ctx.db, sf.id, path)
                discovered++
            }
        }
    }
    call.respond(StartScanResponse(discovered = discovered, alreadyPending = alreadyPending))
}

private fun nextPendingPhoto(ctx: ServerContext): PhotoDto? {
    return Rows.listPhotos(ctx.db).firstOrNull { it.status == "pending" }
}

// endregion

private suspend fun io.ktor.server.application.ApplicationCall.respondError(message: String) {
    respond(MoveResultDto(success = false, errorMessage = message))
}