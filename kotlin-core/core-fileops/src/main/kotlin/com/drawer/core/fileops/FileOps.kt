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

    private companion object {
        const val TEMP_PREFIX = ".atomic-move-"
    }
}