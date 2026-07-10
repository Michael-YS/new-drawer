package com.drawer.core.scanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for [isImageFile], which sniffs magic bytes to distinguish
 * image files from everything else (extension is unreliable — users rename
 * `.exe` to `.jpg`).
 *
 * Covers one positive format and one negative case. More formats (PNG, GIF,
 * WebP, BMP) are added by separate tests so each failure points at one
 * detector branch.
 */
class IsImageFileTest {

    private val jpegMagic = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00,
    )

    private val plainText = "hello world".toByteArray()

    @Test
    fun `isImageFile returns true for content starting with JPEG magic bytes`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "renamed.txt", jpegMagic)

        assertTrue(isImageFile(file, fake))
    }

    @Test
    fun `isImageFile returns false for plain text content`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        val file = fake.newFile(root, "notes.txt", plainText)

        assertFalse(isImageFile(file, fake))
    }
}