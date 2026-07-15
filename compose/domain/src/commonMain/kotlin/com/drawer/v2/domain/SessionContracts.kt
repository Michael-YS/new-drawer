package com.drawer.v2.domain

interface OperationJournalStore {
    suspend fun active(): OperationJournalEntry?
    suspend fun replace(entry: OperationJournalEntry)
    suspend fun clear()
}

interface SuppressionStore {
    suspend fun isSuppressed(
        sourceRootId: String,
        file: StorageRef,
        fingerprint: FileFingerprint,
    ): Boolean

    suspend fun suppress(item: SuppressedItem)
    suspend fun clear(reason: SuppressionReason)
}
