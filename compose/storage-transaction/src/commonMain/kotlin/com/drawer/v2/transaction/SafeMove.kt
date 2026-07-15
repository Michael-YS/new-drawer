package com.drawer.v2.transaction

import com.drawer.v2.domain.OperationJournalEntry
import com.drawer.v2.domain.OperationJournalStore
import com.drawer.v2.domain.OperationStage
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.storage.StorageGateway

data class SafeMoveRequest(
    val operationId: String,
    val source: StorageRef,
    val sourceParent: StorageRef,
    val sourceName: String,
    val targetParent: StorageRef,
    val targetName: String,
)

sealed interface SafeMoveResult {
    data class Completed(val target: StorageRef) : SafeMoveResult
    data class SourceDeleteFailed(val target: StorageRef) : SafeMoveResult
}

sealed interface RecoveryResult {
    data object NoPendingOperation : RecoveryResult
    data object RolledBackTemporary : RecoveryResult
    data object Completed : RecoveryResult
    data class NeedsUserIntervention(val entry: OperationJournalEntry) : RecoveryResult
}

/**
 * The non-overwrite half of the v2 file transaction.
 *
 * A source is deleted only after a verified temp copy has become the final
 * target. A caller handles target collisions by moving the existing target to
 * `Drawer Trash` before invoking this class.
 */
class SafeMove(
    private val storage: StorageGateway,
    private val journal: OperationJournalStore,
) {
    suspend fun execute(request: SafeMoveRequest): SafeMoveResult {
        check(journal.active() == null) { "a file operation is already being recovered" }

        var entry = OperationJournalEntry(
            operationId = request.operationId,
            stage = OperationStage.PREPARED,
            source = request.source,
            sourceParent = request.sourceParent,
            sourceName = request.sourceName,
            targetParent = request.targetParent,
            targetName = request.targetName,
        )
        journal.replace(entry)

        val temporary = storage.createTemporaryFile(request.targetParent, ".drawer-move-")
        entry = entry.copy(stage = OperationStage.TEMP_CREATED, temp = temporary)
        journal.replace(entry)

        try {
            val verification = storage.copy(request.source, temporary)
            check(verification.isValid) { "copied byte count did not match source" }
        } catch (error: Throwable) {
            if (storage.exists(request.source)) runCatching { storage.delete(temporary) }
            journal.clear()
            throw error
        }
        entry = entry.copy(stage = OperationStage.TEMP_COPIED)
        journal.replace(entry)

        val finalTarget = storage.finalizeTemporary(
            temporary = temporary,
            destinationParent = request.targetParent,
            destinationName = request.targetName,
        )
        entry = entry.copy(
            stage = OperationStage.FINALIZED,
            finalTarget = finalTarget,
        )
        journal.replace(entry)

        if (!runCatching { storage.delete(request.source) }.getOrDefault(false)) {
            journal.replace(entry.copy(stage = OperationStage.SOURCE_DELETE_PENDING))
            return SafeMoveResult.SourceDeleteFailed(finalTarget)
        }

        journal.clear()
        return SafeMoveResult.Completed(finalTarget)
    }

    /**
     * Reconciles the one durable operation left by a process death. Recovery
     * never deletes a source unless the final destination is present.
     */
    suspend fun recover(): RecoveryResult {
        val entry = journal.active() ?: return RecoveryResult.NoPendingOperation
        return when (entry.stage) {
            OperationStage.PREPARED,
            OperationStage.TEMP_CREATED,
            OperationStage.TEMP_COPIED,
            OperationStage.EXISTING_TARGET_TRASHED -> {
                entry.temp?.let { runCatching { storage.delete(it) } }
                journal.clear()
                RecoveryResult.RolledBackTemporary
            }

            OperationStage.FINALIZED,
            OperationStage.SOURCE_DELETE_PENDING -> {
                val finalTarget = entry.finalTarget
                    ?: return RecoveryResult.NeedsUserIntervention(entry)
                val sourceExists = storage.exists(entry.source)
                val targetExists = storage.exists(finalTarget)
                when {
                    targetExists && !sourceExists -> {
                        journal.clear()
                        RecoveryResult.Completed
                    }
                    targetExists && sourceExists && runCatching { storage.delete(entry.source) }.getOrDefault(false) -> {
                        journal.clear()
                        RecoveryResult.Completed
                    }
                    targetExists && sourceExists -> RecoveryResult.NeedsUserIntervention(entry)
                    else -> RecoveryResult.NeedsUserIntervention(entry)
                }
            }
        }
    }
}
