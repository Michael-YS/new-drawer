package com.drawer.core.scanner

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Per-format coverage for [isImageFile]. One test per magic signature so a
 * regression in any single detector points at one branch.
 *
 * Real-file fixtures would be more authoritative; the test builds minimal
 * prefixes that match the documented signatures and stops there.
 */
class IsImageFileFormatDetectionTest {

    @Test
    fun `isImageFile returns true for content starting with PNG magic bytes`() {
        val pngMagic = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        )
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "fake.png", pngMagic)

        assertTrue(isImageFile(file, fake))
    }

    @Test
    fun `isImageFile returns true for content starting with GIF magic bytes`() {
        val gifMagic = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "fake.gif", gifMagic)

        assertTrue(isImageFile(file, fake))
    }

    @Test
    fun `isImageFile returns true for content with WebP RIFF + WEBP marker`() {
        val webpMagic = byteArrayOf(
            0x52, 0x49, 0x46, 0x46,
            0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50,
        )
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "fake.webp", webpMagic)

        assertTrue(isImageFile(file, fake))
    }

    @Test
    fun `isImageFile returns true for content starting with BMP magic bytes`() {
        val bmpMagic = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00)
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "fake.bmp", bmpMagic)

        assertTrue(isImageFile(file, fake))
    }
}