package com.drawer.v2.storage

import com.drawer.v2.domain.StorageRef

/** Aggregate for the files currently held below one Drawer Trash directory. */
data class TrashSummary(
    val fileCount: Long = 0,
    val totalBytes: Long = 0,
)

/**
 * Counts regular files recursively while deliberately ignoring unsupported
 * entries such as Windows reparse points.  Callers own the root itself.
 */
suspend fun summarizeDirectory(storage: StorageGateway, directory: StorageRef): TrashSummary {
    var summary = TrashSummary()
    for (entry in storage.listChildren(directory)) {
        when (entry.kind) {
            StorageEntryKind.FILE -> {
                val bytes = entry.metadata?.sizeBytes ?: storage.metadata(entry.ref)?.sizeBytes ?: 0L
                summary = summary.copy(fileCount = summary.fileCount + 1, totalBytes = summary.totalBytes + bytes)
            }
            StorageEntryKind.DIRECTORY -> {
                val child = summarizeDirectory(storage, entry.ref)
                summary = summary.copy(
                    fileCount = summary.fileCount + child.fileCount,
                    totalBytes = summary.totalBytes + child.totalBytes,
                )
            }
            StorageEntryKind.OTHER -> Unit
        }
    }
    return summary
}

/**
 * Deletes only the contents of [directory], keeping the directory as the
 * stable Drawer Trash container. Unsupported entries are never followed or
 * deleted. A failed provider delete aborts so the UI can surface the failure.
 */
suspend fun clearDirectoryContents(storage: StorageGateway, directory: StorageRef): TrashSummary {
    var deleted = TrashSummary()
    for (entry in storage.listChildren(directory)) {
        when (entry.kind) {
            StorageEntryKind.FILE -> {
                val bytes = entry.metadata?.sizeBytes ?: storage.metadata(entry.ref)?.sizeBytes ?: 0L
                check(storage.delete(entry.ref)) { "could not delete ${entry.name}" }
                deleted = deleted.copy(fileCount = deleted.fileCount + 1, totalBytes = deleted.totalBytes + bytes)
            }
            StorageEntryKind.DIRECTORY -> {
                val child = clearDirectoryContents(storage, entry.ref)
                check(storage.delete(entry.ref)) { "could not delete directory ${entry.name}" }
                deleted = deleted.copy(
                    fileCount = deleted.fileCount + child.fileCount,
                    totalBytes = deleted.totalBytes + child.totalBytes,
                )
            }
            StorageEntryKind.OTHER -> Unit
        }
    }
    return deleted
}
