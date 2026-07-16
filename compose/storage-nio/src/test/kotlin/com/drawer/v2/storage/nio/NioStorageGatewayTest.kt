package com.drawer.v2.storage.nio

import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.OperationJournalEntry
import com.drawer.v2.domain.OperationJournalStore
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.SuppressedItem
import com.drawer.v2.domain.SuppressionReason
import com.drawer.v2.domain.SuppressionStore
import com.drawer.v2.scanner.ScanEvent
import com.drawer.v2.scanner.scanImages
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.clearDirectoryContents
import com.drawer.v2.storage.summarizeDirectory
import com.drawer.v2.transaction.SafeMove
import com.drawer.v2.transaction.SafeMoveRequest
import com.drawer.v2.transaction.SafeMoveResult
import com.drawer.v2.transaction.UndoResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
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

    @Test
    fun `metadata includes decodable image dimensions`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-metadata")
        try {
            val image = root.resolve("photo.png")
            ImageIO.write(BufferedImage(12, 7, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
            val metadata = NioStorageGateway().metadata(NioStorageGateway().file(image))

            assertEquals(12, metadata?.width)
            assertEquals(7, metadata?.height)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `Windows reserved directory names are rejected`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-reserved")
        try {
            val gateway = NioStorageGateway()
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                gateway.ensureDirectory(gateway.directory(root), "CON")
            }
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `trash summary and clear preserve the trash root`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-trash")
        try {
            val trash = Files.createDirectories(root.resolve("Drawer Trash"))
            Files.write(trash.resolve("one.jpg"), ByteArray(3))
            Files.createDirectories(trash.resolve("nested"))
            Files.write(trash.resolve("nested/two.jpg"), ByteArray(5))
            val gateway = NioStorageGateway()
            val trashRef = gateway.directory(trash)

            assertEquals(2, summarizeDirectory(gateway, trashRef).fileCount)
            assertEquals(8, summarizeDirectory(gateway, trashRef).totalBytes)
            assertEquals(2, clearDirectoryContents(gateway, trashRef).fileCount)
            assertTrue(Files.isDirectory(trash))
            assertTrue(Files.list(trash).use { !it.findAny().isPresent })
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `scan move and undo use real NIO directories`() = runBlocking {
        val root = Files.createTempDirectory("drawer-nio-journey")
        try {
            val source = Files.createDirectories(root.resolve("source"))
            val target = Files.createDirectories(source.resolve("organized"))
            val incoming = source.resolve("incoming.png")
            ImageIO.write(BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB), "png", incoming.toFile())
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", target.resolve("must-not-scan.png").toFile())
            val gateway = NioStorageGateway()
            val sourceRef = gateway.directory(source)
            val targetRef = gateway.directory(target)

            val discovered = scanImages(
                roots = listOf(SourceRoot("source", sourceRef, "source", available = true)),
                excludedDirectories = setOf(targetRef),
                storage = gateway,
                suppressionStore = NeverSuppressed,
            ).toList().filterIsInstance<ScanEvent.PhotoDiscovered>()

            assertEquals(1, discovered.size)
            val candidate = discovered.single().photo
            assertEquals("incoming.png", candidate.metadata.name)
            val family = gateway.ensureDirectory(targetRef, "Family")
            val result = SafeMove(gateway, MemoryJournal).execute(
                SafeMoveRequest("move-1", candidate.file, candidate.parent, candidate.metadata.name, family, candidate.metadata.name),
            )
            val completed = assertIs<SafeMoveResult.Completed>(result)
            assertFalse(Files.exists(incoming))
            assertTrue(Files.exists(target.resolve("Family/incoming.png")))

            assertIs<UndoResult.Completed>(SafeMove(gateway, MemoryJournal).undo(completed.undo))
            assertTrue(Files.exists(incoming))
            assertFalse(Files.exists(target.resolve("Family/incoming.png")))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private object NeverSuppressed : SuppressionStore {
    override suspend fun isSuppressed(sourceRootId: String, file: StorageRef, fingerprint: FileFingerprint) = false
    override suspend fun suppress(item: SuppressedItem) = Unit
    override suspend fun clear(reason: SuppressionReason) = Unit
}

private object MemoryJournal : OperationJournalStore {
    private var entry: OperationJournalEntry? = null
    override suspend fun active() = entry
    override suspend fun replace(entry: OperationJournalEntry) { this.entry = entry }
    override suspend fun clear() { entry = null }
}
