package com.drawer.server

import com.drawer.core.db.AppDatabase
import com.drawer.core.fileops.FileOps

/**
 * Process-wide context the Ktor routes pull from. Holds the DB
 * connection, the filesystem gateway, and the high-level file-ops
 * facade. Constructed once at startup, passed to [configureServer].
 *
 * The undo log and scan state machine also live here as in-memory
 * structures (per the merged-refactor decision that undo history is
 * server-side and not persisted across restart).
 */
class ServerContext(
    val db: AppDatabase,
    val fileOps: FileOps,
    val gateway: NioFileSystemGateway,
) {
    /**
     * In-memory LIFO stack of recent moves that can be undone in the
     * current session. Cleared when the process restarts per Q4.
     */
    val undoStack: ArrayDeque<UndoRecord> = ArrayDeque()

    /**
     * Tracks active scan sessions by id so cancellation and SSE
     * subscriptions can find them.
     */
    val scans: MutableMap<String, ScanSession> = mutableMapOf()

    companion object {
        /**
         * Production wiring: opens the SQLite DB at the conventional path
         * under the user's app-data dir and constructs a gateway + file-ops
         * on top. Caller (main) supplies [port] only so logs can include it.
         */
        fun forProduction(port: Int): ServerContext {
            val appData = System.getenv("APPDATA")
                ?: error("APPDATA env var missing; cannot resolve data dir")
            val dbDir = java.nio.file.Paths.get(appData, "Drawer")
            java.nio.file.Files.createDirectories(dbDir)
            val db = AppDatabase(dbDir.resolve("drawer.db").toString()).apply { open() }
            val gateway = NioFileSystemGateway()
            return ServerContext(db, FileOps(gateway), gateway)
        }
    }
}

/**
 * Record of a move operation stored in the undo stack. Holds the new
 * location (current entry after move) and the original parent + name
 * to restore to.
 */
data class UndoRecord(
    val moveId: String,
    val currentParentPath: String,
    val currentName: String,
    val originalParentPath: String,
    val originalName: String,
    val photoId: Long,
)

/**
 * One active scan. The CoroutineScope lets cancel() actually stop the
 * walker; the channel feeds SSE subscribers.
 */
class ScanSession(
    val scanId: String,
    val rootPath: String,
    val scope: kotlinx.coroutines.CoroutineScope,
    val progressChannel: kotlinx.coroutines.channels.Channel<String>,
)