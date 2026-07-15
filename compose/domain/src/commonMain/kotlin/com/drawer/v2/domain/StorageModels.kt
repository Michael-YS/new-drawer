package com.drawer.v2.domain

/**
 * A durable, platform-owned storage reference. Shared code may persist and
 * compare this value, but must never parse [token].
 */
data class StorageRef(
    val backend: StorageBackend,
    val token: String,
)

enum class StorageBackend {
    NIO,
    SAF,
}

data class SourceRoot(
    val id: String,
    val directory: StorageRef,
    val displayName: String,
    val available: Boolean,
)

data class TargetRoot(
    val directory: StorageRef,
    val displayName: String,
)

data class FileFingerprint(
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
)

data class MediaMetadata(
    val name: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
    val createdAtEpochMs: Long? = null,
    val capturedAtEpochMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

data class PhotoCandidate(
    val sourceRootId: String,
    val file: StorageRef,
    val parent: StorageRef,
    val metadata: MediaMetadata,
)

enum class SuppressionReason {
    SKIPPED,
    KEPT_COPY,
}

data class SuppressedItem(
    val sourceRootId: String,
    val file: StorageRef,
    val fingerprint: FileFingerprint,
    val reason: SuppressionReason,
)

enum class OperationStage {
    PREPARED,
    EXISTING_TARGET_TRASHED,
    TEMP_COPIED,
    FINALIZED,
    SOURCE_DELETE_PENDING,
}

/** Durable recovery record. At most one operation may be active at a time. */
data class OperationJournalEntry(
    val operationId: String,
    val stage: OperationStage,
    val source: StorageRef,
    val sourceParent: StorageRef,
    val sourceName: String,
    val targetParent: StorageRef,
    val targetName: String,
    val temp: StorageRef? = null,
    val finalTarget: StorageRef? = null,
    val trashedTarget: StorageRef? = null,
)
