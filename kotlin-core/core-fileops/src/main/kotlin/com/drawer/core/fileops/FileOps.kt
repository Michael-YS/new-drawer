package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FileSystemGateway

/**
 * Higher-level filesystem operations built on top of [FileSystemGateway].
 *
 * Stays platform-agnostic: the gateway owns the IO, this class composes
 * read/write/temp/rename primitives into safe move and rollback flows.
 * Tests drive it against [com.drawer.core.scanner.FakeFileSystemGateway]
 * without touching the disk.
 *
 * Construct one instance per request; it is cheap (holds only the gateway
 * reference) and stateless beyond that.
 */
class FileOps(private val gateway: FileSystemGateway) {

    /**
     * Moves [src] under [dstParent] with the new name [dstName].
     *
     * Saga (per refactor-plan §6):
     *  1. Read [src] bytes into memory
     *  2. Create a temp file under [dstParent] with the same prefix
     *  3. Write the bytes into the temp file
     *  4. Delete [src]
     *  5. Atomic-rename the temp file to [dstName]
     *
     * Any exception before step 5 is caught and triggers rollback: the
     * temp file is removed (best-effort) and [src] is left untouched. The
     * exception is rethrown to the caller. After step 5 the move has
     * committed at the OS level and a subsequent crash cannot resurrect
     * the original.
     *
     * @return the new [EntryHandle] representing the moved file at its
     *         destination
     */
    fun atomicMove(src: EntryHandle, dstParent: DirHandle, dstName: String): EntryHandle {
        val bytes = gateway.readAllBytes(src)
        val temp = gateway.createTempFile(dstParent, TEMP_PREFIX)
        try {
            gateway.writeAllBytes(temp, bytes)
            gateway.deleteEntry(src)
            return gateway.atomicRename(temp, dstParent, dstName)
        } catch (e: Exception) {
            runCatching { gateway.deleteEntry(temp) }
            throw e
        }
    }

    /**
     * Inverse of [atomicMove]: moves [current] back to [originalParent]
     * under [originalName]. Same saga and rollback guarantees apply.
     *
     * Callers are responsible for storing the original parent + name (the
     * server-side undo log keeps these per the merged-refactor decision
     * to track undo history in-memory on the backend).
     */
    fun undoMove(current: EntryHandle, originalParent: DirHandle, originalName: String): EntryHandle {
        return atomicMove(current, originalParent, originalName)
    }

    /**
     * Moves [entry] into a `.trash/` subdirectory under [trashParent],
     * renaming to `<basename>__<unixMs><ext>` so files trashed in the
     * same millisecond don't collide.
     *
     * Creates `.trash/` if it does not already exist. The resulting
     * [EntryHandle] lives inside `.trash/` and the original entry is gone
     * from its former location. The unix-millisecond suffix doubles as
     * the trashed-at timestamp the spec records on the photo row.
     *
     * Same saga guarantees as [atomicMove]: failure mid-staging rolls
     * back and leaves the source at its original location.
     */
    fun trashImage(entry: EntryHandle, trashParent: DirHandle): EntryHandle {
        val trashDir = gateway.ensureDirectory(trashParent, TRASH_DIR_NAME)
        val newName = buildTrashName(entry.name, System.currentTimeMillis())
        return atomicMove(entry, trashDir, newName)
    }

    /**
     * Inverse of [trashImage]: moves [trashed] back to [originalParent]
     * under [originalName]. If that name is already taken (rare — usually
     * because the user dropped a new file at the original location while
     * the old one was trashed), append the spec-mandated `_restored`
     * suffix to avoid overwriting.
     *
     * Same saga guarantees as [atomicMove]. The trashed entry disappears
     * from `.trash/` on success; the original-name collision is left
     * untouched.
     */
    fun restoreTrashed(trashed: EntryHandle, originalParent: DirHandle, originalName: String): EntryHandle {
        val targetName = if (destinationIsFree(originalParent, originalName)) {
            originalName
        } else {
            "$originalName$RESTORED_SUFFIX"
        }
        return atomicMove(trashed, originalParent, targetName)
    }

    private fun destinationIsFree(parent: DirHandle, name: String): Boolean {
        return gateway.listChildren(parent).none { it.name == name }
    }

    private fun buildTrashName(originalName: String, timestamp: Long): String {
        val dotIdx = originalName.lastIndexOf('.')
        return if (dotIdx > 0) {
            "${originalName.substring(0, dotIdx)}__$timestamp${originalName.substring(dotIdx)}"
        } else {
            "${originalName}__$timestamp"
        }
    }

    private companion object {
        const val TEMP_PREFIX = ".atomic-move-"
        const val TRASH_DIR_NAME = ".trash"
        const val RESTORED_SUFFIX = "_restored"
    }
}