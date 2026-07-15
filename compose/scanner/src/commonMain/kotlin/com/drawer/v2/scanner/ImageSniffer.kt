package com.drawer.v2.scanner

/** Identifies v1 image formats from leading bytes, never filename extensions. */
fun isSupportedImage(bytes: ByteArray): Boolean =
    isJpeg(bytes) || isPng(bytes) || isGif(bytes) || isWebp(bytes) || isBmp(bytes) || isHeif(bytes)

private fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

private fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
    )

private fun isGif(bytes: ByteArray): Boolean =
    bytes.size >= 6 && (bytes.copyOfRange(0, 6).decodeToString() == "GIF87a" || bytes.copyOfRange(0, 6).decodeToString() == "GIF89a")

private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP"

private fun isBmp(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()

private fun isHeif(bytes: ByteArray): Boolean {
    if (bytes.size < 12 || bytes.copyOfRange(4, 8).decodeToString() != "ftyp") return false
    val brand = bytes.copyOfRange(8, 12).decodeToString()
    return brand in setOf("heic", "heix", "hevc", "heim", "mif1", "msf1")
}
