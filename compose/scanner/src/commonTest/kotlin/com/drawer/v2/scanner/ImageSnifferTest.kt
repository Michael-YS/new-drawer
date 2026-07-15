package com.drawer.v2.scanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageSnifferTest {
    @Test
    fun `recognizes every v1 format`() {
        assertTrue(isSupportedImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(isSupportedImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertTrue(isSupportedImage("GIF89a".encodeToByteArray()))
        assertTrue(isSupportedImage("RIFF0000WEBP".encodeToByteArray()))
        assertTrue(isSupportedImage("BM".encodeToByteArray()))
        assertTrue(isSupportedImage(byteArrayOf(0, 0, 0, 0, *"ftypheic".encodeToByteArray())))
    }

    @Test
    fun `rejects a renamed non image`() {
        assertFalse(isSupportedImage("not a photo".encodeToByteArray()))
    }
}
