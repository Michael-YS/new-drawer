package com.drawer.server

import com.drawer.core.db.AppDatabase
import java.sql.Statement

/**
 * Thin row mapper for SQL <-> Kotlin. Lives next to the routes that
 * use it; promotes to a real repository module if it grows past two
 * tables or gains any cross-table invariants.
 */
internal object Rows {

    fun insertSourceFolder(db: AppDatabase, path: String, displayName: String, recursive: Boolean, addedAt: Long): Long {
        val conn = db.connection
        conn.prepareStatement(
            "INSERT INTO source_folders (path, display_name, enabled, recursive, added_at) VALUES (?, ?, 1, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { stmt ->
            stmt.setString(1, path)
            stmt.setString(2, displayName)
            stmt.setBoolean(3, recursive)
            stmt.setLong(4, addedAt)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs ->
                return if (rs.next()) rs.getLong(1) else -1
            }
        }
    }

    fun listSourceFolders(db: AppDatabase): List<SourceFolderDto> {
        val conn = db.connection
        val out = mutableListOf<SourceFolderDto>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, path, display_name, enabled, recursive, added_at FROM source_folders").use { rs ->
                while (rs.next()) {
                    out += SourceFolderDto(
                        id = rs.getLong("id"),
                        path = rs.getString("path"),
                        displayName = rs.getString("display_name"),
                        enabled = rs.getInt("enabled") != 0,
                        recursive = rs.getInt("recursive") != 0,
                        addedAt = rs.getLong("added_at"),
                    )
                }
            }
        }
        return out
    }

    fun findSourceFolder(db: AppDatabase, id: Long): SourceFolderDto? {
        val conn = db.connection
        conn.prepareStatement(
            "SELECT id, path, display_name, enabled, recursive, added_at FROM source_folders WHERE id = ?",
        ).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return SourceFolderDto(
                    id = rs.getLong("id"),
                    path = rs.getString("path"),
                    displayName = rs.getString("display_name"),
                    enabled = rs.getInt("enabled") != 0,
                    recursive = rs.getInt("recursive") != 0,
                    addedAt = rs.getLong("added_at"),
                )
            }
        }
    }

    fun deleteSourceFolder(db: AppDatabase, id: Long): Boolean {
        val conn = db.connection
        conn.prepareStatement("DELETE FROM source_folders WHERE id = ?").use { stmt ->
            stmt.setLong(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    fun insertTargetRootDir(db: AppDatabase, path: String, displayName: String, isDefault: Boolean, addedAt: Long): Long {
        val conn = db.connection
        if (isDefault) {
            conn.createStatement().use { it.executeUpdate("UPDATE target_root_dirs SET is_default = 0") }
        }
        conn.prepareStatement(
            "INSERT INTO target_root_dirs (path, display_name, is_default, added_at) VALUES (?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { stmt ->
            stmt.setString(1, path)
            stmt.setString(2, displayName)
            stmt.setBoolean(3, isDefault)
            stmt.setLong(4, addedAt)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs -> return if (rs.next()) rs.getLong(1) else -1 }
        }
    }

    fun listTargetRootDirs(db: AppDatabase): List<TargetRootDirDto> {
        val conn = db.connection
        val out = mutableListOf<TargetRootDirDto>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, path, display_name, is_default, added_at FROM target_root_dirs ORDER BY id").use { rs ->
                while (rs.next()) {
                    out += TargetRootDirDto(
                        id = rs.getLong("id"),
                        path = rs.getString("path"),
                        displayName = rs.getString("display_name"),
                        isDefault = rs.getInt("is_default") != 0,
                        addedAt = rs.getLong("added_at"),
                    )
                }
            }
        }
        return out
    }

    fun deleteTargetRootDir(db: AppDatabase, id: Long): Boolean {
        val conn = db.connection
        conn.prepareStatement("DELETE FROM target_root_dirs WHERE id = ?").use { stmt ->
            stmt.setLong(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    fun insertTargetFolder(db: AppDatabase, rootDirId: Long, name: String, displayName: String, sortOrder: Int): Long {
        val conn = db.connection
        conn.prepareStatement(
            """INSERT INTO target_folders (root_dir_id, name, display_name, sort_order)
               VALUES (?, ?, ?, ?)""",
            Statement.RETURN_GENERATED_KEYS,
        ).use { stmt ->
            stmt.setLong(1, rootDirId)
            stmt.setString(2, name)
            stmt.setString(3, displayName)
            stmt.setInt(4, sortOrder)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs -> return if (rs.next()) rs.getLong(1) else -1 }
        }
    }

    fun listTargetFolders(db: AppDatabase, rootDirId: Long? = null): List<TargetFolderDto> {
        val conn = db.connection
        val out = mutableListOf<TargetFolderDto>()
        val sql = buildString {
            append("SELECT id, root_dir_id, name, display_name, sort_order, last_used_at FROM target_folders")
            if (rootDirId != null) append(" WHERE root_dir_id = ?")
            append(" ORDER BY sort_order, id")
        }
        if (rootDirId != null) {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, rootDirId)
                stmt.executeQuery().use(::mapTargetFolderRows).let(out::addAll)
            }
        } else {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use(::mapTargetFolderRows).let(out::addAll)
            }
        }
        return out
    }

    private fun mapTargetFolderRows(rs: java.sql.ResultSet): List<TargetFolderDto> {
        val out = mutableListOf<TargetFolderDto>()
        while (rs.next()) {
            val lastUsed = rs.getLong("last_used_at")
            out += TargetFolderDto(
                id = rs.getLong("id"),
                rootDirId = rs.getLong("root_dir_id"),
                name = rs.getString("name"),
                displayName = rs.getString("display_name"),
                sortOrder = rs.getInt("sort_order"),
                lastUsedAt = if (rs.wasNull()) null else lastUsed,
            )
        }
        return out
    }

    fun touchTargetFolderLastUsed(db: AppDatabase, id: Long, timestamp: Long) {
        db.connection.prepareStatement(
            "UPDATE target_folders SET last_used_at = ? WHERE id = ?",
        ).use { stmt ->
            stmt.setLong(1, timestamp)
            stmt.setLong(2, id)
            stmt.executeUpdate()
        }
    }

    fun deleteTargetFolder(db: AppDatabase, id: Long): Boolean {
        db.connection.prepareStatement("DELETE FROM target_folders WHERE id = ?").use { stmt ->
            stmt.setLong(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    fun putSetting(db: AppDatabase, key: String, value: String) {
        db.connection.prepareStatement(
            """INSERT INTO settings (key, value) VALUES (?, ?)
               ON CONFLICT(key) DO UPDATE SET value = excluded.value""",
        ).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.executeUpdate()
        }
    }

    fun getSetting(db: AppDatabase, key: String): String? {
        db.connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs -> return if (rs.next()) rs.getString("value") else null }
        }
    }

    fun getAllSettings(db: AppDatabase): Map<String, String> {
        val conn = db.connection
        val out = mutableMapOf<String, String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT key, value FROM settings").use { rs ->
                while (rs.next()) out[rs.getString("key")] = rs.getString("value")
            }
        }
        return out
    }

    fun insertPhoto(db: AppDatabase, sourceFolderId: Long, entryPath: String): Long {
        val conn = db.connection
        conn.prepareStatement(
            """INSERT INTO photos (source_folder_id, entry_handle, status)
               VALUES (?, ?, 'pending')""",
            Statement.RETURN_GENERATED_KEYS,
        ).use { stmt ->
            stmt.setLong(1, sourceFolderId)
            stmt.setBytes(2, entryPath.toByteArray(Charsets.UTF_8))
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs -> return if (rs.next()) rs.getLong(1) else -1 }
        }
    }

    fun getPhoto(db: AppDatabase, id: Long): PhotoDto? {
        val conn = db.connection
        conn.prepareStatement(
            """SELECT id, source_folder_id, entry_handle, status, destination_handle, original_handle,
                      trashed_at, processed_at
               FROM photos WHERE id = ?""",
        ).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return mapPhotoRow(rs)
            }
        }
    }

    fun listPhotos(db: AppDatabase, sourceFolderId: Long? = null): List<PhotoDto> {
        val conn = db.connection
        val out = mutableListOf<PhotoDto>()
        val sql = buildString {
            append("""SELECT id, source_folder_id, entry_handle, status, destination_handle, original_handle,
                          trashed_at, processed_at FROM photos""")
            if (sourceFolderId != null) append(" WHERE source_folder_id = ?")
            append(" ORDER BY id")
        }
        val rows = if (sourceFolderId != null) {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, sourceFolderId)
                stmt.executeQuery().use(::mapPhotoRowSet).also(out::addAll)
            }
        } else {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use(::mapPhotoRowSet).also(out::addAll)
            }
        }
        return out
    }

    private fun mapPhotoRowSet(rs: java.sql.ResultSet): List<PhotoDto> {
        val out = mutableListOf<PhotoDto>()
        while (rs.next()) out += mapPhotoRow(rs)
        return out
    }

    private fun mapPhotoRow(rs: java.sql.ResultSet): PhotoDto {
        val entryBytes: ByteArray? = rs.getBytes("entry_handle")
        val destBytes: ByteArray? = rs.getBytes("destination_handle")
        val origBytes: ByteArray? = rs.getBytes("original_handle")
        return PhotoDto(
            id = rs.getLong("id"),
            sourceFolderId = rs.getLong("source_folder_id"),
            entryPath = entryBytes?.toString(Charsets.UTF_8),
            status = rs.getString("status"),
            destinationPath = destBytes?.toString(Charsets.UTF_8),
            originalPath = origBytes?.toString(Charsets.UTF_8),
            trashedAt = rs.getLong("trashed_at").takeIf { !rs.wasNull() },
            processedAt = rs.getLong("processed_at").takeIf { !rs.wasNull() },
        )
    }

    fun markPhotoMoved(db: AppDatabase, id: Long, destinationPath: String, processedAt: Long) {
        db.connection.prepareStatement(
            """UPDATE photos SET status = 'done', destination_handle = ?, processed_at = ?
               WHERE id = ?""",
        ).use { stmt ->
            stmt.setBytes(1, destinationPath.toByteArray(Charsets.UTF_8))
            stmt.setLong(2, processedAt)
            stmt.setLong(3, id)
            stmt.executeUpdate()
        }
    }

    fun markPhotoUndone(db: AppDatabase, id: Long) {
        db.connection.prepareStatement(
            """UPDATE photos SET status = 'pending', destination_handle = NULL, processed_at = NULL
               WHERE id = ?""",
        ).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeUpdate()
        }
    }

    fun markPhotoTrashed(db: AppDatabase, id: Long, originalPath: String, trashedAt: Long) {
        db.connection.prepareStatement(
            """UPDATE photos SET status = 'trashed', original_handle = ?, destination_handle = NULL,
                      trashed_at = ?, processed_at = ?
               WHERE id = ?""",
        ).use { stmt ->
            stmt.setBytes(1, originalPath.toByteArray(Charsets.UTF_8))
            stmt.setLong(2, trashedAt)
            stmt.setLong(3, trashedAt)
            stmt.setLong(4, id)
            stmt.executeUpdate()
        }
    }

    fun resetPhotoStatuses(db: AppDatabase) {
        db.connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """UPDATE photos
                   SET status = 'pending', destination_handle = NULL, original_handle = NULL,
                       trashed_at = NULL, processed_at = NULL
                   WHERE status IN ('done', 'skipped')""",
            )
        }
    }

    fun sourceFolderPath(db: AppDatabase, id: Long): String? {
        db.connection.prepareStatement("SELECT path FROM source_folders WHERE id = ?").use { stmt ->
            stmt.setLong(1, id)
            stmt.executeQuery().use { rs -> return if (rs.next()) rs.getString("path") else null }
        }
    }
}