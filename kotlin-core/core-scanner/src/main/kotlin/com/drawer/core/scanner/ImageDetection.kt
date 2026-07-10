package com.drawer.core.scanner

/**
 * Returns true when [entry]'s leading bytes match a known image magic
 * signature. Pulls bytes through [gateway.readMagicBytes], so the gateway
 * impl decides where the bytes come from — never reads the file directly.
 *
 * Covers the formats the refactor plan explicitly targets: JPEG, PNG, GIF,
 * WebP, BMP. HEIC is out of scope (decision: drop HEIC support).
 *
 * Returns false when the entry is shorter than the shortest signature, or
 * when no signature matches. Callers should branch on [FileSystemGateway.isDirectory]
 * first if they may pass directory entries.
 */
fun isImageFile(entry: EntryHandle, gateway: FileSystemGateway): Boolean {
    val bytes = gateway.readMagicBytes(entry, MAX_MAGIC_LEN)
    return matchesAnySignature(bytes)
}

private const val MAX_MAGIC_LEN = 12

private fun matchesAnySignature(bytes: ByteArray): Boolean {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return true
    }
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() && bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) {
        return true
    }
    if (bytes.size >= 4 &&
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()
    ) {
        return true
    }
    if (bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
    ) {
        return true
    }
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
        return true
    }
    return false
}