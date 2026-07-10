package com.drawer.server

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FileSystemGateway
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Windows implementation of [FileSystemGateway] backed by
 * [java.nio.file]. This is the only module allowed to import
 * `java.nio.file.*` per the refactor-plan §3.5 boundary rule.
 *
 * The Android side gets its own `SafFileSystemGateway` living in the
 * `:android-native-module`; both satisfy the same interface so the
 * upper layers (scanner, file-ops, Ktor routes) stay platform-agnostic.
 *
 * Handles are opaque wrappers around [Path]; tests and external code use
 * [dirOf] / [entryOf] to seed trees from real filesystem locations.
 */
class NioFileSystemGateway : FileSystemGateway {

    /**
     * Wraps [path] as a [DirHandle] suitable for passing to [listChildren]
     * or any other gateway method. Safe to call with paths that don't yet
     * exist on disk as long as the caller never invokes methods that
     * require the path to be present.
     */
    fun dirOf(path: Path): DirHandle = NioDir(path)

    /**
     * Wraps [path] as an [EntryHandle]. No filesystem check is performed.
     */
    fun entryOf(path: Path): EntryHandle = NioEntry(path)

    private inner class NioDir(val path: Path) : DirHandle {
        override fun toString(): String = "NioDir($path)"
    }

    private inner class NioEntry(val path: Path) : EntryHandle {
        override val name: String
            get() = path.fileName?.toString() ?: path.toString()
        override fun toString(): String = "NioEntry($path)"
    }

    override fun listChildren(dir: DirHandle): List<EntryHandle> {
        val d = dir as NioDir
        return Files.list(d.path).use { stream ->
            stream.map { NioEntry(it) }.toList()
        }
    }

    override fun isDirectory(entry: EntryHandle): Boolean {
        val e = entry as NioEntry
        return Files.isDirectory(e.path)
    }

    override fun readMagicBytes(entry: EntryHandle, len: Int): ByteArray {
        require(len >= 0) { "len must be non-negative, got $len" }
        val e = entry as NioEntry
        val bytes = ByteArray(len)
        Files.newInputStream(e.path).use { stream ->
            val read = stream.read(bytes)
            return if (read < 0) ByteArray(0) else bytes.copyOf(read)
        }
    }

    override fun dirHandle(entry: EntryHandle): DirHandle? {
        val e = entry as NioEntry
        return if (Files.isDirectory(e.path)) NioDir(e.path) else null
    }

    override fun readAllBytes(entry: EntryHandle): ByteArray {
        val e = entry as NioEntry
        return Files.readAllBytes(e.path)
    }

    override fun writeAllBytes(entry: EntryHandle, bytes: ByteArray) {
        val e = entry as NioEntry
        Files.write(e.path, bytes)
    }

    override fun createTempFile(parent: DirHandle, prefix: String): EntryHandle {
        val p = parent as NioDir
        val tempFile = Files.createTempFile(p.path, prefix, ".tmp")
        return NioEntry(tempFile)
    }

    override fun atomicRename(src: EntryHandle, dstParent: DirHandle, dstName: String): EntryHandle {
        val s = src as NioEntry
        val dst = (dstParent as NioDir).path.resolve(dstName)
        Files.move(s.path, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return NioEntry(dst)
    }

    override fun deleteEntry(entry: EntryHandle): Boolean {
        val e = entry as NioEntry
        return if (Files.exists(e.path)) {
            Files.delete(e.path)
            true
        } else {
            true
        }
    }

    override fun ensureDirectory(parent: DirHandle, name: String): DirHandle {
        val p = parent as NioDir
        val dirPath = p.path.resolve(name)
        Files.createDirectories(dirPath)
        return NioDir(dirPath)
    }
}