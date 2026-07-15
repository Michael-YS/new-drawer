package com.drawer.v2.transaction

import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.OperationJournalEntry
import com.drawer.v2.domain.OperationJournalStore
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.storage.CopyVerification
import com.drawer.v2.storage.StorageEntry
import com.drawer.v2.storage.StorageGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeMoveTest {
    @Test
    fun `successful move finalizes before deleting source`() = runTest {
        val source = file("source/photo.jpg")
        val target = directory("target")
        val storage = FakeStorage(mapOf(source to 42L))
        val journal = FakeJournal()

        val result = SafeMove(storage, journal).execute(
            request(source = source, target = target),
        )

        val completed = assertIs<SafeMoveResult.Completed>(result)
        assertEquals(42L, storage.sizeOf(completed.target))
        assertTrue(!storage.exists(source))
        assertNull(journal.entry)
    }

    @Test
    fun `delete failure retains source and records recovery state`() = runTest {
        val source = file("source/photo.jpg")
        val target = directory("target")
        val storage = FakeStorage(mapOf(source to 42L), deleteSucceeds = false)
        val journal = FakeJournal()

        val result = SafeMove(storage, journal).execute(
            request(source = source, target = target),
        )

        val failed = assertIs<SafeMoveResult.SourceDeleteFailed>(result)
        assertTrue(storage.exists(source))
        assertEquals(42L, storage.sizeOf(failed.target))
        assertEquals(com.drawer.v2.domain.OperationStage.SOURCE_DELETE_PENDING, journal.entry?.stage)
    }

    private fun request(source: StorageRef, target: StorageRef) = SafeMoveRequest(
        operationId = "op-1",
        source = source,
        sourceParent = directory("source"),
        sourceName = "photo.jpg",
        targetParent = target,
        targetName = "photo.jpg",
    )

    private fun file(token: String) = StorageRef(StorageBackend.NIO, token)
    private fun directory(token: String) = StorageRef(StorageBackend.NIO, token)
}

private class FakeJournal : OperationJournalStore {
    var entry: OperationJournalEntry? = null

    override suspend fun active(): OperationJournalEntry? = entry
    override suspend fun replace(entry: OperationJournalEntry) {
        this.entry = entry
    }
    override suspend fun clear() {
        entry = null
    }
}

private class FakeStorage(
    initialFiles: Map<StorageRef, Long>,
    private val deleteSucceeds: Boolean = true,
) : StorageGateway {
    private val files = initialFiles.toMutableMap()
    private var sequence = 0

    override suspend fun listChildren(directory: StorageRef): List<StorageEntry> = emptyList()
    override suspend fun metadata(file: StorageRef): MediaMetadata? = files[file]?.let {
        MediaMetadata(name = file.token.substringAfterLast('/'), sizeBytes = it, modifiedAtEpochMs = 1)
    }
    override suspend fun readPrefix(file: StorageRef, maxBytes: Int): ByteArray = ByteArray(0)
    override suspend fun findChild(directory: StorageRef, name: String): StorageEntry? = null
    override suspend fun ensureDirectory(parent: StorageRef, name: String): StorageRef =
        StorageRef(parent.backend, "${parent.token}/$name")

    override suspend fun createTemporaryFile(parent: StorageRef, prefix: String): StorageRef =
        StorageRef(parent.backend, "${parent.token}/$prefix${sequence++}")

    override suspend fun copy(source: StorageRef, destination: StorageRef): CopyVerification {
        val size = checkNotNull(files[source])
        files[destination] = size
        return CopyVerification(sourceBytes = size, copiedBytes = size)
    }

    override suspend fun finalizeTemporary(
        temporary: StorageRef,
        destinationParent: StorageRef,
        destinationName: String,
    ): StorageRef {
        val size = checkNotNull(files.remove(temporary))
        return StorageRef(destinationParent.backend, "${destinationParent.token}/$destinationName")
            .also { files[it] = size }
    }

    override suspend fun delete(ref: StorageRef): Boolean {
        if (!deleteSucceeds) return false
        files.remove(ref)
        return true
    }

    override suspend fun exists(ref: StorageRef): Boolean = files.containsKey(ref)
    fun sizeOf(ref: StorageRef): Long? = files[ref]
}
