package com.drawer.core.scanner

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [scanImagesFlow]. Drives the flow to completion against
 * a small fake tree and checks that every image file is emitted and that
 * the final events include [ScanEvent.DirScanned] for the root and
 * [ScanEvent.Completed].
 *
 * Recursion into subdirectories is covered separately so a regression in
 * one dimension points at one branch.
 */
class ScanImagesFlowTest {

    private val jpegMagic = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
    )
    private val plainText = "hello".toByteArray()

    @Test
    fun `scanImagesFlow emits ImageFound for each image file and finishes with DirScanned plus Completed`() = runTest {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        fake.newFile(root, "a.jpg", jpegMagic)
        fake.newFile(root, "notes.txt", plainText)
        fake.newFile(
            root,
            "b.png",
            byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            ),
        )

        val events = scanImagesFlow(root, fake).toList()

        val imageNames = events.filterIsInstance<ScanEvent.ImageFound>().map { it.entry.name }.toSet()
        assertEquals(setOf("a.jpg", "b.png"), imageNames)

        val dirScannedCount = events.count { it is ScanEvent.DirScanned }
        assertEquals(1, dirScannedCount)

        assertTrue(events.last() is ScanEvent.Completed, "expected flow to end with Completed, got ${events.last()}")
    }
}