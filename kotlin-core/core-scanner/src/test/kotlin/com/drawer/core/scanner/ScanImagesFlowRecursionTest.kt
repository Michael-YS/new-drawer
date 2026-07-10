package com.drawer.core.scanner

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Recursion contract test for [scanImagesFlow]. Builds a tree with one
 * subdirectory, asserts that image files inside the subdirectory are
 * discovered alongside top-level ones.
 */
class ScanImagesFlowRecursionTest {

    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val pngMagic = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    )

    @Test
    fun `scanImagesFlow recurses into subdirectories and emits images found inside`() = runTest {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val subdir = fake.newNestedDir(root, "subdir")
        fake.newFile(subdir, "nested.jpg", jpegMagic)
        fake.newFile(root, "top.png", pngMagic)
        fake.newFile(subdir, "ignored.txt", "hello".toByteArray())

        val events = scanImagesFlow(root, fake).toList()
        val imageNames = events.filterIsInstance<ScanEvent.ImageFound>().map { it.entry.name }.toSet()

        assertEquals(setOf("nested.jpg", "top.png"), imageNames)
    }
}