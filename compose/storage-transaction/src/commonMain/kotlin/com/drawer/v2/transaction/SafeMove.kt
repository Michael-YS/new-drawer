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
    val overwrite: ExistingTargetToTrash? = null,
)

/** A conflict resolution selected by the user: retain the old file in trash. */
data class ExistingTargetToTrash(
    val existingTarget: StorageRef,
    val trashParent: StorageRef,
    val trashName: String,
)

sealed interface SafeMoveResult {
    data class Completed(
        val target: StorageRef,
        /** Memory-only token; callers intentionally discard it when the session ends. */
        val undo: SessionUndo,
    ) : SafeMoveResult
    data class SourceDeleteFailed(val target: StorageRef) : SafeMoveResult
}

/**
 * Enough information to reverse one already-completed move during the current
 * process lifetime. It is deliberately not persisted: closing the app ends an
 * undo session, while Drawer Trash remains available in the file system.
 */
data class SessionUndo(
    val sourceParent: StorageRef,
    val sourceName: String,
    val target: StorageRef,
    val targetParent: StorageRef,
    val targetName: String,
    val displacedTarget: StorageRef? = null,
    val trashedTarget: StorageRef? = null,
)

sealed interface UndoResult {
    data object Completed : UndoResult
    data class NeedsUserIntervention(val detail: String) : UndoResult
}

sealed interface RecoveryResult {
    data object NoPendingOperation : RecoveryResult
    data object RolledBackTemporary : RecoveryResult
    data object Completed : RecoveryResult
    data class NeedsUserIntervention(val entry: OperationJournalEntry) : RecoveryResult
}

/**
 * A source is deleted only after a verified temp copy has become the final
 * target. For an overwrite, the old target is first copied to `Drawer Trash`;
 * every state up to the final source move can restore that old target.
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
            existingTarget = request.overwrite?.existingTarget,
            trashParent = request.overwrite?.trashParent,
            trashName = request.overwrite?.trashName,
        )
        journal.replace(entry)

        request.overwrite?.let { overwrite ->
            require(storage.exists(overwrite.existingTarget)) { "overwrite target no longer exists" }
            val trashTemporary = storage.createTemporaryFile(overwrite.trashParent, ".drawer-trash-")
            entry = entry.copy(stage = OperationStage.TRASH_TEMP_CREATED, temp = trashTemporary)
            journal.replace(entry)

            try {
                val verification = storage.copy(overwrite.existingTarget, trashTemporary)
                check(verification.isValid) { "trash copy byte count did not match existing target" }
            } catch (error: Throwable) {
                runCatching { storage.delete(trashTemporary) }
                journal.clear()
                throw error
            }
            entry = entry.copy(stage = OperationStage.TRASH_TEMP_COPIED)
            journal.replace(entry)

            val trashedTarget = storage.finalizeTemporary(
                temporary = trashTemporary,
                destinationParent = overwrite.trashParent,
                destinationName = overwrite.trashName,
            )
            entry = entry.copy(
                stage = OperationStage.TRASH_FINALIZED,
                temp = null,
                trashedTarget = trashedTarget,
            )
            journal.replace(entry)

            if (!deleteWithRetries(overwrite.existingTarget)) {
                runCatching { storage.delete(trashedTarget) }
                journal.clear()
                throw IllegalStateException("could not move existing target to Drawer Trash")
            }
            entry = entry.copy(stage = OperationStage.EXISTING_TARGET_TRASHED)
            journal.replace(entry)
        }

        try {
            val temporary = storage.createTemporaryFile(request.targetParent, ".drawer-move-")
            entry = entry.copy(stage = OperationStage.TEMP_CREATED, temp = temporary)
            journal.replace(entry)

            val verification = storage.copy(request.source, temporary)
            check(verification.isValid) { "copied byte count did not match source" }
            entry = entry.copy(stage = OperationStage.TEMP_COPIED)
            journal.replace(entry)

            val finalTarget = storage.finalizeTemporary(
                temporary = temporary,
                destinationParent = request.targetParent,
                destinationName = request.targetName,
            )
            entry = entry.copy(
                stage = OperationStage.FINALIZED,
                temp = null,
                finalTarget = finalTarget,
            )
            journal.replace(entry)

            if (!deleteWithRetries(request.source)) {
                journal.replace(entry.copy(stage = OperationStage.SOURCE_DELETE_PENDING))
                return SafeMoveResult.SourceDeleteFailed(finalTarget)
            }

            journal.clear()
            return SafeMoveResult.Completed(
                target = finalTarget,
                undo = SessionUndo(
                    sourceParent = request.sourceParent,
                    sourceName = request.sourceName,
                    target = finalTarget,
                    targetParent = request.targetParent,
                    targetName = request.targetName,
                    displacedTarget = entry.existingTarget,
                    trashedTarget = entry.trashedTarget,
                ),
            )
        } catch (error: Throwable) {
            // The source has not been deleted in this block. If an overwrite
            // was already staged, restore its old target before surfacing the
            // failure; the durable journal remains only when restoration fails.
            rollbackBeforeSourceMove(entry)
            throw error
        }
    }

    /**
     * Reconciles the one durable operation left by a process death. Recovery
     * never deletes a source unless the final destination is present.
     */
    suspend fun recover(): RecoveryResult {
        val entry = journal.active() ?: return RecoveryResult.NoPendingOperation
        return when (entry.stage) {
            OperationStage.PREPARED -> rollbackBeforeSourceMove(entry)

            OperationStage.TRASH_TEMP_CREATED,
            OperationStage.TRASH_TEMP_COPIED -> {
                entry.temp?.let { runCatching { storage.delete(it) } }
                journal.clear()
                RecoveryResult.RolledBackTemporary
            }

            OperationStage.TRASH_FINALIZED -> rollbackFinalizedTrash(entry)

            OperationStage.EXISTING_TARGET_TRASHED,
            OperationStage.TEMP_CREATED,
            OperationStage.TEMP_COPIED -> rollbackBeforeSourceMove(entry)

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
                    targetExists && sourceExists && deleteWithRetries(entry.source) -> {
                        journal.clear()
                        RecoveryResult.Completed
                    }
                    targetExists && sourceExists -> RecoveryResult.NeedsUserIntervention(entry)
                    else -> RecoveryResult.NeedsUserIntervention(entry)
                }
            }
        }
    }

    /**
     * User-selected rollback after the final copy exists but the source could
     * not be deleted. The source is never touched; the final copy is removed
     * only while both files still exist. An overwritten target is restored from
     * Drawer Trash as part of the same recovery record.
     */
    suspend fun rollbackPendingSourceDelete(): RecoveryResult {
        val entry = journal.active() ?: return RecoveryResult.NoPendingOperation
        if (entry.stage != OperationStage.SOURCE_DELETE_PENDING) return intervention(entry)
        val finalTarget = entry.finalTarget ?: return intervention(entry)
        if (!storage.exists(entry.source) || !storage.exists(finalTarget)) return intervention(entry)
        val sourceSize = storage.metadata(entry.source)?.sizeBytes ?: return intervention(entry)
        val targetSize = storage.metadata(finalTarget)?.sizeBytes ?: return intervention(entry)
        if (sourceSize != targetSize) return intervention(entry)
        if (!deleteWithRetries(finalTarget)) return intervention(entry)
        return if (entry.existingTarget == null) {
            journal.clear()
            RecoveryResult.RolledBackTemporary
        } else {
            restoreExistingTarget(entry)
        }
    }

    /**
     * Best-effort, session-only reversal of a completed move. The forward
     * target stays in place until a verified copy has been finalized at its
     * original source name. If any later delete/restore step fails, both the
     * known-good copy and any trash copy are retained for manual resolution.
     */
    suspend fun undo(move: SessionUndo): UndoResult {
        if (!storage.exists(move.target)) return UndoResult.NeedsUserIntervention("the moved file is missing")
        if (storage.findChild(move.sourceParent, move.sourceName) != null) {
            return UndoResult.NeedsUserIntervention("the original source name is already occupied")
        }

        val restoredSource = try {
            val temporary = storage.createTemporaryFile(move.sourceParent, ".drawer-undo-")
            val verification = storage.copy(move.target, temporary)
            check(verification.isValid) { "undo source copy byte count did not match" }
            storage.finalizeTemporary(temporary, move.sourceParent, move.sourceName)
        } catch (_: Throwable) {
            return UndoResult.NeedsUserIntervention("could not restore the original source copy")
        }
        if (!storage.exists(restoredSource)) return UndoResult.NeedsUserIntervention("restored source could not be verified")
        if (!deleteWithRetries(move.target)) {
            return UndoResult.NeedsUserIntervention("source restored, but the moved copy could not be deleted")
        }

        val displaced = move.displacedTarget ?: return UndoResult.Completed
        val trashed = move.trashedTarget
            ?: return UndoResult.NeedsUserIntervention("the overwritten file has no trash record")
        if (storage.exists(displaced)) return UndoResult.NeedsUserIntervention("the overwritten target name is occupied")
        if (!storage.exists(trashed)) return UndoResult.NeedsUserIntervention("the overwritten file is missing from Drawer Trash")
        return try {
            val temporary = storage.createTemporaryFile(move.targetParent, ".drawer-undo-restore-")
            val verification = storage.copy(trashed, temporary)
            check(verification.isValid) { "undo trash restore byte count did not match" }
            storage.finalizeTemporary(temporary, move.targetParent, move.targetName)
            if (!deleteWithRetries(trashed)) {
                UndoResult.NeedsUserIntervention("overwritten target restored, but its trash copy could not be deleted")
            } else {
                UndoResult.Completed
            }
        } catch (_: Throwable) {
            UndoResult.NeedsUserIntervention("could not restore the overwritten target from Drawer Trash")
        }
    }

    private suspend fun rollbackFinalizedTrash(entry: OperationJournalEntry): RecoveryResult {
        val existing = entry.existingTarget ?: return intervention(entry)
        val trashed = entry.trashedTarget ?: return intervention(entry)
        return when {
            storage.exists(existing) -> {
                runCatching { storage.delete(trashed) }
                journal.clear()
                RecoveryResult.RolledBackTemporary
            }
            storage.exists(trashed) -> restoreExistingTarget(entry)
            else -> intervention(entry)
        }
    }

    private suspend fun rollbackBeforeSourceMove(entry: OperationJournalEntry): RecoveryResult {
        entry.temp?.let { runCatching { storage.delete(it) } }
        return if (entry.existingTarget == null) {
            journal.clear()
            RecoveryResult.RolledBackTemporary
        } else {
            restoreExistingTarget(entry)
        }
    }

    private suspend fun restoreExistingTarget(entry: OperationJournalEntry): RecoveryResult {
        val existing = entry.existingTarget ?: return intervention(entry)
        val trashed = entry.trashedTarget ?: return intervention(entry)
        if (storage.exists(existing)) {
            journal.clear()
            return RecoveryResult.RolledBackTemporary
        }
        if (!storage.exists(trashed)) return intervention(entry)

        return try {
            val temporary = storage.createTemporaryFile(entry.targetParent, ".drawer-restore-")
            val verification = storage.copy(trashed, temporary)
            check(verification.isValid) { "trash restore byte count did not match" }
            storage.finalizeTemporary(temporary, entry.targetParent, entry.targetName)
            if (!deleteWithRetries(trashed)) return intervention(entry)
            journal.clear()
            RecoveryResult.RolledBackTemporary
        } catch (_: Throwable) {
            intervention(entry)
        }
    }

    private suspend fun deleteWithRetries(ref: StorageRef): Boolean =
        (1..3).any { runCatching { storage.delete(ref) }.getOrDefault(false) }

    private fun intervention(entry: OperationJournalEntry) = RecoveryResult.NeedsUserIntervention(entry)
}
