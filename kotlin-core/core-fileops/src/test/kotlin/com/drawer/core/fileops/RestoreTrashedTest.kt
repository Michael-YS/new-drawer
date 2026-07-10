package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FakeFileSystemGateway
import com.drawer.core.scanner.FileSystemGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [FileOps.restoreTrashed]. Covers the two paths:
 *  - the destination slot is free, so the original name is used verbatim;
 *  - the destination slot is occupied, so the spec's `_restored` suffix
 *    avoids overwriting whatever is there.
 */
class RestoreTrashedTest {

    @Test
    fun `restoreTrashed moves the trashed entry back to its original parent and name`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val src = fake.newFile(root, "photo.jpg", "data".toByteArray())

        val ops = FileOps(fake)
        val trashed = ops.trashImage(src, root)
        val restored = ops.restoreTrashed(trashed, root, "photo.jpg")

        assertEquals("photo.jpg", restored.name)
        assertEquals("data", String(fake.readAllBytes(restored)))
        assertTrue(fake.listChildren(root).any { it.name == "photo.jpg" })
    }

    @Test
    fun `restoreTrashed appends _restored suffix when the destination name is occupied`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val src = fake.newFile(root, "photo.jpg", "original".toByteArray())

        val ops = FileOps(fake)
        val trashed = ops.trashImage(src, root)

        // Occupy the destination slot while the file is in trash
        fake.newFile(root, "photo.jpg", "new-content".toByteArray())

        val restored = ops.restoreTrashed(trashed, root, "photo.jpg")

        assertEquals("photo.jpg_restored", restored.name)
        assertEquals("original", String(fake.readAllBytes(restored)))
    }
}