package com.drawer.v2.persistence

import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.OperationJournalEntry
import com.drawer.v2.domain.OperationJournalStore
import com.drawer.v2.domain.OperationStage
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.SuppressedItem
import com.drawer.v2.domain.SuppressionReason
import com.drawer.v2.domain.SuppressionStore
import com.drawer.v2.domain.TargetRoot

class SqlDelightOperationJournalStore(
    private val database: DrawerDatabase,
) : OperationJournalStore {
    private val queries get() = database.drawerQueries

    override suspend fun active(): OperationJournalEntry? = queries.selectJournal(
        mapper = { _, operationId, stage, sourceBackend, sourceToken, sourceParentBackend, sourceParentToken,
            sourceName, targetParentBackend, targetParentToken, targetName, tempBackend, tempToken,
            finalBackend, finalToken, trashBackend, trashToken ->
            OperationJournalEntry(
                operationId = operationId,
                stage = OperationStage.valueOf(stage),
                source = ref(sourceBackend, sourceToken),
                sourceParent = ref(sourceParentBackend, sourceParentToken),
                sourceName = sourceName,
                targetParent = ref(targetParentBackend, targetParentToken),
                targetName = targetName,
                temp = refOrNull(tempBackend, tempToken),
                finalTarget = refOrNull(finalBackend, finalToken),
                trashedTarget = refOrNull(trashBackend, trashToken),
            )
        },
    ).executeAsOneOrNull()

    override suspend fun replace(entry: OperationJournalEntry) {
        queries.replaceJournal(
            operation_id = entry.operationId,
            stage = entry.stage.name,
            source_backend = entry.source.backend.name,
            source_token = entry.source.token,
            source_parent_backend = entry.sourceParent.backend.name,
            source_parent_token = entry.sourceParent.token,
            source_name = entry.sourceName,
            target_parent_backend = entry.targetParent.backend.name,
            target_parent_token = entry.targetParent.token,
            target_name = entry.targetName,
            temp_backend = entry.temp?.backend?.name,
            temp_token = entry.temp?.token,
            final_backend = entry.finalTarget?.backend?.name,
            final_token = entry.finalTarget?.token,
            trash_backend = entry.trashedTarget?.backend?.name,
            trash_token = entry.trashedTarget?.token,
        )
    }

    override suspend fun clear() {
        queries.clearJournal()
    }
}

class SqlDelightSuppressionStore(
    private val database: DrawerDatabase,
) : SuppressionStore {
    private val queries get() = database.drawerQueries

    override suspend fun isSuppressed(
        sourceRootId: String,
        file: StorageRef,
        fingerprint: FileFingerprint,
    ): Boolean = queries.selectSuppressedItem(
        source_root_id = sourceRootId,
        token = file.token,
        size_bytes = fingerprint.sizeBytes,
        modified_at_ms = fingerprint.modifiedAtEpochMs,
    ).executeAsOneOrNull() != null

    override suspend fun suppress(item: SuppressedItem) {
        queries.upsertSuppressedItem(
            source_root_id = item.sourceRootId,
            backend = item.file.backend.name,
            token = item.file.token,
            size_bytes = item.fingerprint.sizeBytes,
            modified_at_ms = item.fingerprint.modifiedAtEpochMs,
            reason = item.reason.name,
        )
    }

    override suspend fun clear(reason: SuppressionReason) {
        queries.deleteSuppressedByReason(reason.name)
    }
}

class SqlDelightConfigurationStore(
    private val database: DrawerDatabase,
) {
    private val queries get() = database.drawerQueries

    fun sourceRoots(): List<SourceRoot> = queries.selectSourceRoots(
        mapper = { id, backend, token, displayName, available ->
            SourceRoot(id, ref(backend, token), displayName, available != 0L)
        },
    ).executeAsList()

    fun saveSourceRoot(root: SourceRoot) {
        queries.upsertSourceRoot(
            root.id,
            root.directory.backend.name,
            root.directory.token,
            root.displayName,
            if (root.available) 1L else 0L,
        )
    }

    fun removeSourceRoot(id: String) {
        queries.deleteSourceRoot(id)
    }

    fun targetRoot(): TargetRoot? = queries.selectCurrentTarget(
        mapper = { _, backend, token, displayName -> TargetRoot(ref(backend, token), displayName) },
    ).executeAsOneOrNull()

    fun saveTargetRoot(target: TargetRoot) {
        queries.upsertCurrentTarget(target.directory.backend.name, target.directory.token, target.displayName)
        queries.upsertTrashRoot(target.directory.backend.name, target.directory.token, target.displayName)
    }

    fun categoryFirstSeen(target: TargetRoot, name: String, nowEpochMs: Long) {
        queries.upsertCategoryFirstSeen(target.directory.token, name, nowEpochMs)
    }
}

private fun ref(backend: String, token: String): StorageRef = StorageRef(StorageBackend.valueOf(backend), token)
private fun refOrNull(backend: String?, token: String?): StorageRef? =
    if (backend == null || token == null) null else ref(backend, token)
