package com.drawer.v2.storage.nio

import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.storage.CopyVerification
import com.drawer.v2.storage.StorageEntry
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.StorageGateway
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.attribute.BasicFileAttributes

/** Windows implementation. Reparse points are surfaced as [StorageEntryKind.OTHER]. */
class NioStorageGateway : StorageGateway {
    fun directory(path: Path): StorageRef = ref(path)
    fun file(path: Path): StorageRef = ref(path)

    override suspend fun listChildren(directory: StorageRef): List<StorageEntry> {
        val parent = path(directory)
        Files.newDirectoryStream(parent).use { stream ->
            return stream.map { child ->
                val attributes = runCatching {
                    Files.readAttributes(child, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                }.getOrNull()
                val kind = when {
                    Files.isSymbolicLink(child) || attributes?.isOther == true -> StorageEntryKind.OTHER
                    attributes?.isDirectory == true -> StorageEntryKind.DIRECTORY
                    attributes?.isRegularFile == true -> StorageEntryKind.FILE
                    else -> StorageEntryKind.OTHER
                }
                StorageEntry(
                    ref = ref(child),
                    parent = directory,
                    name = child.fileName.toString(),
                    kind = kind,
                    metadata = if (kind == StorageEntryKind.FILE) metadata(ref(child)) else null,
                )
            }.toList()
        }
    }

    override suspend fun metadata(file: StorageRef): MediaMetadata? {
        val filePath = path(file)
        if (!Files.isRegularFile(filePath, NOFOLLOW_LINKS)) return null
        val attrs = Files.readAttributes(filePath, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        return MediaMetadata(
            name = filePath.fileName.toString(),
            sizeBytes = attrs.size(),
            modifiedAtEpochMs = attrs.lastModifiedTime().toMillis(),
            createdAtEpochMs = attrs.creationTime().toMillis(),
        )
    }

    override suspend fun readPrefix(file: StorageRef, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "maxBytes must not be negative" }
        val buffer = ByteArray(maxBytes)
        Files.newInputStream(path(file)).use { input ->
            val count = input.read(buffer)
            return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
        }
    }

    override suspend fun findChild(directory: StorageRef, name: String): StorageEntry? =
        listChildren(directory).firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun ensureDirectory(parent: StorageRef, name: String): StorageRef {
        validateSegment(name)
        return ref(Files.createDirectories(path(parent).resolve(name)))
    }

    override suspend fun createTemporaryFile(parent: StorageRef, prefix: String): StorageRef =
        ref(Files.createTempFile(path(parent), prefix, ".tmp"))

    override suspend fun copy(source: StorageRef, destination: StorageRef): CopyVerification {
        val sourcePath = path(source)
        val destinationPath = path(destination)
        var copied = 0L
        BufferedInputStream(Files.newInputStream(sourcePath)).use { input ->
            BufferedOutputStream(
                Files.newOutputStream(destinationPath, CREATE, TRUNCATE_EXISTING),
            ).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                }
                output.flush()
            }
        }
        return CopyVerification(
            sourceBytes = Files.size(sourcePath),
            copiedBytes = copied,
        )
    }

    override suspend fun finalizeTemporary(
        temporary: StorageRef,
        destinationParent: StorageRef,
        destinationName: String,
    ): StorageRef {
        validateSegment(destinationName)
        val target = path(destinationParent).resolve(destinationName)
        require(!Files.exists(target, NOFOLLOW_LINKS)) { "destination already exists: $target" }
        val temp = path(temporary)
        try {
            Files.move(temp, target, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target)
        }
        return ref(target)
    }

    override suspend fun delete(ref: StorageRef): Boolean = Files.deleteIfExists(path(ref))
    override suspend fun exists(ref: StorageRef): Boolean = Files.exists(path(ref), NOFOLLOW_LINKS)

    private fun ref(path: Path): StorageRef = StorageRef(
        backend = StorageBackend.NIO,
        token = path.toAbsolutePath().normalize().toString(),
    )

    private fun path(ref: StorageRef): Path {
        require(ref.backend == StorageBackend.NIO) { "NIO gateway cannot resolve ${ref.backend}" }
        return Path.of(ref.token)
    }

    private fun validateSegment(name: String) {
        require(name.isNotBlank()) { "name must not be blank" }
        require(name.none { it in "\\/:*?\"<>|" }) { "name contains an unsupported character" }
        require(!name.endsWith('.') && !name.endsWith(' ')) { "name must not end in a dot or space" }
    }
}
