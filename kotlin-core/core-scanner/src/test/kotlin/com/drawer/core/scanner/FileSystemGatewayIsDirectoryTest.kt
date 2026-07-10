package com.drawer.core.scanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for [FileSystemGateway.isDirectory].
 *
 * Lets traversal code distinguish a directory entry from a file entry after
 * [FileSystemGateway.listChildren] returns a polymorphic [EntryHandle].
 */
class FileSystemGatewayIsDirectoryTest {

    @Test
    fun `isDirectory returns true for a subdirectory entry and false for a file entry`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val subdir = fake.newDir(root, "subdir")
        val file = fake.newFile(root, "a.jpg")

        assertTrue(fake.isDirectory(subdir))
        assertFalse(fake.isDirectory(file))
    }
}