package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FakeFileSystemGateway
import com.drawer.core.scanner.FileSystemGateway
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Failure-path coverage for [FileOps.atomicMove]. Asserts that any
 * exception raised inside the saga triggers rollback: the source is left
 * intact and the staging temp is removed.
 *
 * Uses an anonymous gateway that delegates to [FakeFileSystemGateway] for
 * everything except [FileSystemGateway.writeAllBytes], which throws — the
 * same point where a real "disk full" or permission failure would surface.
 */
class AtomicMoveRollbackTest {

    @Test
    fun `atomicMove rolls back when writeAllBytes fails - source preserved, temp gone`() {
        val fake = FakeFileSystemGateway()
        val srcParent = fake.newDir("srcParent")
        val dstParent = fake.newDir("dstParent")
        val src = fake.newFile(srcParent, "doc.txt", "original".toByteArray())

        val throwingGateway = object : FileSystemGateway by fake {
            override fun writeAllBytes(entry: EntryHandle, bytes: ByteArray) {
                throw IOException("simulated disk full")
            }
        }

        val ops = FileOps(throwingGateway)

        try {
            ops.atomicMove(src, dstParent, "moved.txt")
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("simulated disk full", e.message)
        }

        assertTrue(
            fake.listChildren(srcParent).any { it.name == "doc.txt" },
            "source file should still be present after rollback",
        )
        assertTrue(
            fake.listChildren(dstParent).none { it.name.startsWith(".atomic-move-") },
            "temp file should be cleaned up after rollback",
        )
    }
}