package com.drawer.v2.storage.saf

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.storage.CopyVerification
import com.drawer.v2.storage.StorageEntry
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.StorageGateway
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.util.UUID

/** Local/SD-card SAF gateway. Tokens retain the tree grant and document URI. */
class SafStorageGateway(private val context: Context) : StorageGateway {
    private val resolver get() = context.contentResolver

    fun isSupportedLocalTree(treeUri: Uri): Boolean =
        treeUri.authority == EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY && DocumentsContract.isTreeUri(treeUri)

    fun directoryFromTreeUri(treeUri: Uri): StorageRef {
        require(isSupportedLocalTree(treeUri)) { "only local or SD-card document trees are supported" }
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return ref(treeUri, DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId))
    }

    /** True when [candidate] is [ancestor] or sits beneath it in the local/SD tree. */
    fun isSameOrDescendant(candidate: StorageRef, ancestor: StorageRef): Boolean {
        val candidateUri = token(candidate).documentUri
        val ancestorUri = token(ancestor).documentUri
        if (candidateUri.authority != ancestorUri.authority) return false
        val candidateId = DocumentsContract.getDocumentId(candidateUri)
        val ancestorId = DocumentsContract.getDocumentId(ancestorUri)
        return candidateId == ancestorId || candidateId.startsWith("$ancestorId/")
    }

    override suspend fun listChildren(directory: StorageRef): List<StorageEntry> {
        val parent = token(directory)
        val parentId = DocumentsContract.getDocumentId(parent.documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.treeUri, parentId)
        resolver.query(childrenUri, CHILD_PROJECTION, null, null, null).use { cursor ->
            if (cursor == null) throw FileNotFoundException(childrenUri.toString())
            return buildList {
                while (cursor.moveToNext()) add(entryFromCursor(parent.treeUri, directory, cursor))
            }
        }
    }

    override suspend fun metadata(file: StorageRef): MediaMetadata? {
        val token = token(file)
        resolver.query(token.documentUri, DOCUMENT_PROJECTION, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return null
            return metadataFromCursor(cursor)
        }
    }

    override suspend fun readPrefix(file: StorageRef, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "maxBytes must not be negative" }
        val uri = token(file).documentUri
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.use {
            val buffer = ByteArray(maxBytes)
            val count = it.read(buffer)
            return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
        }
    }

    override suspend fun findChild(directory: StorageRef, name: String): StorageEntry? =
        listChildren(directory).firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun ensureDirectory(parent: StorageRef, name: String): StorageRef {
        validateSegment(name)
        findChild(parent, name)?.let {
            require(it.kind == StorageEntryKind.DIRECTORY) { "a file already uses the requested directory name" }
            return it.ref
        }
        val parentToken = token(parent)
        val created = DocumentsContract.createDocument(
            resolver,
            parentToken.documentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: error("cannot create directory $name")
        return ref(parentToken.treeUri, created)
    }

    override suspend fun createTemporaryFile(parent: StorageRef, prefix: String): StorageRef {
        val parentToken = token(parent)
        val created = DocumentsContract.createDocument(
            resolver,
            parentToken.documentUri,
            "application/octet-stream",
            "$prefix${UUID.randomUUID()}.tmp",
        ) ?: error("cannot create temporary file")
        return ref(parentToken.treeUri, created)
    }

    override suspend fun copy(source: StorageRef, destination: StorageRef): CopyVerification {
        val sourceUri = token(source).documentUri
        val destinationUri = token(destination).documentUri
        val expected = metadata(source)?.sizeBytes ?: 0L
        var copied = 0L
        val input = resolver.openInputStream(sourceUri) ?: throw FileNotFoundException(sourceUri.toString())
        val output = resolver.openOutputStream(destinationUri, "wt") ?: throw FileNotFoundException(destinationUri.toString())
        BufferedInputStream(input).use { bufferedInput ->
            BufferedOutputStream(output).use { bufferedOutput ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = bufferedInput.read(buffer)
                    if (count < 0) break
                    bufferedOutput.write(buffer, 0, count)
                    copied += count
                }
                bufferedOutput.flush()
            }
        }
        return CopyVerification(expected, copied)
    }

    override suspend fun finalizeTemporary(temporary: StorageRef, destinationParent: StorageRef, destinationName: String): StorageRef {
        validateSegment(destinationName)
        require(findChild(destinationParent, destinationName) == null) { "destination already exists" }
        val temp = token(temporary)
        runCatching {
            DocumentsContract.renameDocument(resolver, temp.documentUri, destinationName)
        }.getOrNull()?.let { return ref(temp.treeUri, it) }

        val parent = token(destinationParent)
        val destination = DocumentsContract.createDocument(resolver, parent.documentUri, "application/octet-stream", destinationName)
            ?: error("cannot create final destination")
        val finalRef = ref(parent.treeUri, destination)
        check(copy(temporary, finalRef).isValid) { "temporary copy verification failed" }
        check(delete(temporary)) { "cannot remove temporary file after finalization" }
        return finalRef
    }

    override suspend fun delete(ref: StorageRef): Boolean =
        try {
            DocumentsContract.deleteDocument(resolver, token(ref).documentUri)
        } catch (_: FileNotFoundException) {
            true
        }

    override suspend fun exists(ref: StorageRef): Boolean = metadata(ref) != null

    private fun entryFromCursor(treeUri: Uri, parent: StorageRef, cursor: Cursor): StorageEntry {
        val id = cursor.string(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val name = cursor.string(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mime = cursor.string(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        val ref = ref(treeUri, documentUri)
        val kind = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) StorageEntryKind.DIRECTORY else StorageEntryKind.FILE
        return StorageEntry(
            ref = ref,
            parent = parent,
            name = name,
            kind = kind,
            metadata = if (kind == StorageEntryKind.FILE) metadataFromCursor(cursor) else null,
        )
    }

    private fun metadataFromCursor(cursor: Cursor): MediaMetadata = MediaMetadata(
        name = cursor.string(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        sizeBytes = cursor.long(DocumentsContract.Document.COLUMN_SIZE),
        modifiedAtEpochMs = cursor.long(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
    )

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun ref(treeUri: Uri, documentUri: Uri) = StorageRef(StorageBackend.SAF, "$treeUri\n$documentUri")

    private fun token(ref: StorageRef): SafToken {
        require(ref.backend == StorageBackend.SAF) { "SAF gateway cannot resolve ${ref.backend}" }
        val pieces = ref.token.split('\n', limit = 2)
        require(pieces.size == 2) { "invalid SAF reference" }
        return SafToken(Uri.parse(pieces[0]), Uri.parse(pieces[1]))
    }

    private fun validateSegment(name: String) {
        require(name.isNotBlank() && name.none { it in "\\/:*?\"<>|" } && !name.endsWith('.') && !name.endsWith(' '))
    }

    private data class SafToken(val treeUri: Uri, val documentUri: Uri)

    private companion object {
        const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
