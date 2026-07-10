package com.drawer.core.fileops

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FakeFileSystemGateway
import com.drawer.core.scanner.FileSystemGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [FileOps.trashImage]. Asserts that trashing an entry
 * moves it under `<trashParent>/.trash/` with a `<base>__<ts>.<ext>` name,
 * preserves content, removes the source, and ensures `.trash/` exists.
 */
class TrashImageTest {

    @Test
    fun `trashImage moves the entry into a trash subdirectory with a timestamp suffix`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val src = fake.newFile(root, "doc.txt", "data".toByteArray())

        val ops = FileOps(fake)
        val trashed = ops.trashImage(src, root)

        assertTrue(trashed.name.startsWith("doc__"), "name=${trashed.name}")
        assertTrue(trashed.name.endsWith(".txt"), "name=${trashed.name}")
        assertEquals("data", String(fake.readAllBytes(trashed)))

        val trashDirEntry = fake.listChildren(root).first { it.name == ".trash" }
        assertTrue(fake.isDirectory(trashDirEntry))

        assertTrue(fake.listChildren(root).none { it.name == "doc.txt" })
    }

    @Test
    fun `trashImage for a file without extension still produces a timestamped name`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val src = fake.newFile(root, "README", "hi".toByteArray())

        val ops = FileOps(fake)
        val trashed = ops.trashImage(src, root)

        assertTrue(trashed.name.startsWith("README__"), "name=${trashed.name}")
        assertEquals("hi", String(fake.readAllBytes(trashed)))
    }
}