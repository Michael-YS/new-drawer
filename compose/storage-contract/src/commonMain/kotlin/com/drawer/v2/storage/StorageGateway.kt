package com.drawer.v2.storage

import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.StorageRef

enum class StorageEntryKind {
    FILE,
    DIRECTORY,
    /** Symlinks, Windows reparse points, and unsupported provider entries. */
    OTHER,
}

data class StorageEntry(
    val ref: StorageRef,
    val parent: StorageRef,
    val name: String,
    val kind: StorageEntryKind,
    val metadata: MediaMetadata? = null,
)

data class CopyVerification(
    val sourceBytes: Long,
    val copiedBytes: Long,
) {
    val isValid: Boolean get() = sourceBytes == copiedBytes
}

/**
 * Platform boundary for a user-authorized storage provider.
 *
 * Implementations must stream copies and return durable [StorageRef] values.
 * Calls are serialized by the session coordinator; this interface deliberately
 * does not promise a cross-provider atomic rename.
 */
interface StorageGateway {
    suspend fun listChildren(directory: StorageRef): List<StorageEntry>
    suspend fun metadata(file: StorageRef): MediaMetadata?
    suspend fun readPrefix(file: StorageRef, maxBytes: Int): ByteArray
    suspend fun findChild(directory: StorageRef, name: String): StorageEntry?
    suspend fun ensureDirectory(parent: StorageRef, name: String): StorageRef
    suspend fun createTemporaryFile(parent: StorageRef, prefix: String): StorageRef
    suspend fun copy(source: StorageRef, destination: StorageRef): CopyVerification
    suspend fun finalizeTemporary(
        temporary: StorageRef,
        destinationParent: StorageRef,
        destinationName: String,
    ): StorageRef

    /** Idempotent: deleting an already-missing reference returns true. */
    suspend fun delete(ref: StorageRef): Boolean
    suspend fun exists(ref: StorageRef): Boolean
}
