package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FakeFileSystemGateway
import com.drawer.core.scanner.FileSystemGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [FileOps.undoMove]. Builds an atomicMove, then calls
 * undoMove with the move's source parent + name, and asserts the file
 * reappears at the original location with intact content while the
 * destination is empty.
 */
class UndoMoveTest {

    @Test
    fun `undoMove relocates the moved entry back to its original parent and name`() {
        val fake = FakeFileSystemGateway()
        val srcParent = fake.newDir("srcParent")
        val dstParent = fake.newDir("dstParent")
        val src = fake.newFile(srcParent, "original.txt", "data".toByteArray())

        val ops = FileOps(fake)
        val moved = ops.atomicMove(src, dstParent, "moved.txt")
        val undone = ops.undoMove(moved, srcParent, "original.txt")

        assertEquals("original.txt", undone.name)
        assertEquals("data", String(fake.readAllBytes(undone)))
        assertTrue(fake.listChildren(srcParent).any { it.name == "original.txt" })
        assertTrue(fake.listChildren(dstParent).none { it.name == "moved.txt" })
    }
}