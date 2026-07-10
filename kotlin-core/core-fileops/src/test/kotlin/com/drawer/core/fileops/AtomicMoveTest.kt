package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FakeFileSystemGateway
import com.drawer.core.scanner.FileSystemGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [FileOps.atomicMove] happy path.
 *
 * Builds a tiny tree, calls atomicMove, then asserts: the new entry exists
 * at the destination with the same content; the source no longer appears in
 * its former parent's children list; no leftover temp file remains in the
 * destination.
 */
class AtomicMoveTest {

    @Test
    fun `atomicMove copies content to destination, removes source, and leaves no temp`() {
        val fake = FakeFileSystemGateway()
        val srcParent = fake.newDir("srcParent")
        val dstParent = fake.newDir("dstParent")
        val src = fake.newFile(srcParent, "doc.txt", "hello world".toByteArray())

        val ops = FileOps(fake)
        val moved = ops.atomicMove(src, dstParent, "moved.txt")

        assertEquals("moved.txt", moved.name)
        assertEquals("hello world", String(fake.readAllBytes(moved)))
        assertTrue(fake.listChildren(srcParent).none { it.name == "doc.txt" })
        assertTrue(fake.listChildren(dstParent).none { it.name.startsWith(".atomic-move-") })
    }
}