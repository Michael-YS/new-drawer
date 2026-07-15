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
        val verification = storage.copy(request.source, temporary)
        check(verification.isValid) { "copied byte count did not match source" }
        entry = entry.copy(stage = OperationStage.TEMP_COPIED, temp = temporary)
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

        if (!storage.delete(request.source)) {
            journal.replace(entry.copy(stage = OperationStage.SOURCE_DELETE_PENDING))
            return SafeMoveResult.SourceDeleteFailed(finalTarget)
        }

        journal.clear()
        return SafeMoveResult.Completed(finalTarget)
    }
}
