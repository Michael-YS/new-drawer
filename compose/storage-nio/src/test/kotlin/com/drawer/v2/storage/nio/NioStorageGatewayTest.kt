package com.drawer.v2.storage.nio

import com.drawer.v2.storage.StorageEntryKind
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NioStorageGatewayTest {
    @Test
    fun `copy streams into temporary file and finalizes without replacing`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-test")
        try {
            val source = root.resolve("source.jpg")
            Files.write(source, ByteArray(32_768) { it.toByte() })
            val gateway = NioStorageGateway()
            val rootRef = gateway.directory(root)
            val sourceRef = gateway.file(source)
            val temp = gateway.createTemporaryFile(rootRef, ".drawer-test-")

            val verification = gateway.copy(sourceRef, temp)
            val final = gateway.finalizeTemporary(temp, rootRef, "target.jpg")

            assertTrue(verification.isValid)
            assertEquals(32_768, gateway.metadata(final)?.sizeBytes)
            assertTrue(gateway.exists(sourceRef))
            assertFalse(gateway.exists(temp))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `directory listing exposes a normal file`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-list")
        try {
            Files.writeString(root.resolve("photo.jpg"), "photo")
            val gateway = NioStorageGateway()

            val entry = gateway.findChild(gateway.directory(root), "PHOTO.JPG")

            assertNotNull(entry)
            assertEquals(StorageEntryKind.FILE, entry.kind)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
