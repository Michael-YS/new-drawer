package com.drawer.android.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FileSystemGateway
import java.io.InputStream

/**
 * Android implementation of [FileSystemGateway] backed by the Storage
 * Access Framework. Wraps [DocumentFile] and [Context.getContentResolver]
 * so the gateway interface stays the only contract the upper layers
 * (scanner, file-ops) see.
 *
 * Per refactor-plan §3.5 this is the only module in the rewrite
 * allowed to import `android.*`. Each handle is an opaque wrapper
 * around a SAF tree or document URI; callers must not inspect the URI
 * bytes directly.
 *
 * **Testing note**: SAF behavior depends on Android system services
 * (`DocumentFile` provider, `ContentResolver`). JVM unit tests cannot
 * exercise the real surface — instrumented tests on a device or
 * emulator are required. The class is structured so that production
 * code wraps only [DocumentFile] / [ContentResolver]; an emulator test
 * can drive the real API end-to-end.
 */
class SafFileSystemGateway(private val context: Context) : FileSystemGateway {

    private val contentResolver get() = context.contentResolver

    private inner class SafDir(val treeUri: Uri, val relativePath: String) : DirHandle {
        override fun toString(): String = "SafDir($treeUri#$relativePath)"
    }

    private inner class SafEntry(
        val treeUri: Uri,
        val relativePath: String,
        val documentUri: Uri,
        override val name: String,
        val isDirectoryFlag: Boolean,
    ) : EntryHandle {
        override fun toString(): String = "SafEntry($documentUri)"
    }

    /**
     * Entry point for picking a directory via `ACTION_OPEN_DOCUMENT_TREE`.
     * Returns null if the URI is not a tree URI; the caller should call
     * `takePersistableUriPermission` first.
     */
    fun dirFromTreeUri(treeUri: Uri): DirHandle = SafDir(treeUri, relativePath = "")

    override fun listChildren(dir: DirHandle): List<EntryHandle> {
        val d = dir as SafDir
        val parent = DocumentFile.fromTreeUri(context, d.treeUri)
            ?: return emptyList()
        val resolved = resolve(parent, d.relativePath)
        return resolved.listFiles().map { doc ->
            SafEntry(
                treeUri = d.treeUri,
                relativePath = joinPath(d.relativePath, doc.name ?: ""),
                documentUri = doc.uri,
                name = doc.name ?: "",
                isDirectoryFlag = doc.isDirectory,
            )
        }
    }

    override fun isDirectory(entry: EntryHandle): Boolean =
        (entry as SafEntry).isDirectoryFlag

    override fun readMagicBytes(entry: EntryHandle, len: Int): ByteArray {
        require(len >= 0) { "len must be non-negative, got $len" }
        val e = entry as SafEntry
        val stream: InputStream = contentResolver.openInputStream(e.documentUri)
            ?: return ByteArray(0)
        return stream.use { input ->
            val buf = ByteArray(len)
            val read = input.read(buf)
            if (read < 0) ByteArray(0) else buf.copyOf(read)
        }
    }

    override fun dirHandle(entry: EntryHandle): DirHandle? {
        val e = entry as SafEntry
        if (!e.isDirectoryFlag) return null
        return SafDir(e.treeUri, e.relativePath)
    }

    override fun readAllBytes(entry: EntryHandle): ByteArray {
        val e = entry as SafEntry
        val stream = contentResolver.openInputStream(e.documentUri)
            ?: return ByteArray(0)
        return stream.use { it.readBytes() }
    }

    override fun writeAllBytes(entry: EntryHandle, bytes: ByteArray) {
        val e = entry as SafEntry
        val stream = contentResolver.openOutputStream(e.documentUri, "wt")
            ?: error("cannot open output stream for ${e.documentUri}")
        stream.use { it.write(bytes) }
    }

    override fun createTempFile(parent: DirHandle, prefix: String): EntryHandle {
        val d = parent as SafDir
        val resolved = resolve(DocumentFile.fromTreeUri(context, d.treeUri)!!, d.relativePath)
        val name = "$prefix${System.nanoTime()}.tmp"
        val doc = resolved.createFile("application/octet-stream", name)
            ?: error("cannot create temp file in $parent")
        return SafEntry(
            treeUri = d.treeUri,
            relativePath = joinPath(d.relativePath, name),
            documentUri = doc.uri,
            name = name,
            isDirectoryFlag = false,
        )
    }

    override fun atomicRename(src: EntryHandle, dstParent: DirHandle, dstName: String): EntryHandle {
        val s = src as SafEntry
        val d = dstParent as SafDir
        val parent = resolve(DocumentFile.fromTreeUri(context, d.treeUri)!!, d.relativePath)
        val existing = parent.findFile(dstName)
        if (existing != null) {
            existing.delete()
        }
        val dstDoc = parent.createFile("application/octet-stream", dstName)
            ?: error("cannot create destination $dstName in $dstParent")
        val dstEntry = SafEntry(
            treeUri = d.treeUri,
            relativePath = joinPath(d.relativePath, dstName),
            documentUri = dstDoc.uri,
            name = dstName,
            isDirectoryFlag = false,
        )
        val bytes = readAllBytes(s)
        writeAllBytes(dstEntry, bytes)
        DocumentFile.fromSingleUri(context, s.documentUri)?.delete()
        return dstEntry
    }

    override fun deleteEntry(entry: EntryHandle): Boolean {
        val e = entry as SafEntry
        val doc = DocumentFile.fromSingleUri(context, e.documentUri) ?: return true
        return doc.delete()
    }

    override fun ensureDirectory(parent: DirHandle, name: String): DirHandle {
        val d = parent as SafDir
        val resolved = resolve(DocumentFile.fromTreeUri(context, d.treeUri)!!, d.relativePath)
        val existing = resolved.findFile(name)
        val dir = if (existing?.isDirectory == true) {
            existing
        } else {
            resolved.createDirectory(name) ?: error("cannot create directory $name under $parent")
        }
        return SafDir(d.treeUri, joinPath(d.relativePath, name))
    }

    private fun resolve(root: DocumentFile, relativePath: String): DocumentFile {
        if (relativePath.isEmpty()) return root
        var current = root
        for (segment in relativePath.split('/')) {
            if (segment.isEmpty()) continue
            val next = current.findFile(segment)
                ?: error("segment $segment not found under ${current.uri}")
            current = next
        }
        return current
    }

    private fun joinPath(base: String, segment: String): String =
        if (base.isEmpty()) segment else "$base/$segment"
}