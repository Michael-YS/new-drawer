package com.drawer.core.scanner

/**
 * In-memory [FileSystemGateway] for tests.
 *
 * Backed by a map of `DirHandle -> MutableList<EntryHandle>`; no filesystem,
 * no Android SDK, no async. Lets scanner and file-ops tests run as plain
 * JVM unit tests.
 *
 * Construct, seed with [newDir] / [newFile], then exercise the [FileSystemGateway]
 * surface against the returned handles.
 */
class FakeFileSystemGateway : FileSystemGateway {

    private inner class FakeDir(overrideName: String) : DirHandle {
        override fun toString(): String = "FakeDir($name)"
        val name: String = overrideName
    }

    private sealed class FakeEntry : EntryHandle

    private inner class FakeFileEntry(
        overrideName: String,
        val content: ByteArray = ByteArray(0),
    ) : FakeEntry() {
        override val name: String = overrideName
    }

    private inner class FakeSubdirEntry(
        overrideName: String,
        val asDir: FakeDir,
    ) : FakeEntry(), DirHandle {
        override val name: String = overrideName
        override fun toString(): String = "FakeSubdirEntry($name)"
    }

    private val roots = mutableListOf<FakeDir>()
    private val children = mutableMapOf<FakeDir, MutableList<FakeEntry>>()

    /**
     * Creates a standalone directory handle with no parent. Use it as the
     * root for [listChildren] in tests.
     */
    fun newDir(name: String): DirHandle {
        val dir = FakeDir(name)
        roots += dir
        children.getOrPut(dir) { mutableListOf() }
        return dir
    }

    /**
     * Creates a child directory entry under [parent] and returns it as an
     * [EntryHandle]. Use this when the test only needs to verify the
     * entry shows up in [listChildren] and that [isDirectory] reports
     * true; it does not give the caller a handle to add files inside.
     */
    fun newDir(parent: DirHandle, name: String): EntryHandle {
        val dir = parent as FakeDir
        val childDir = FakeDir(name)
        roots += childDir
        children.getOrPut(childDir) { mutableListOf() }
        val entry = FakeSubdirEntry(name, childDir)
        children.getOrPut(dir) { mutableListOf() } += entry
        return entry
    }

    /**
     * Creates a child directory under [parent] and returns a [DirHandle]
     * pointing at it so the caller can add nested files or further
     * subdirs underneath. The child is also discoverable via
     * [listChildren] on [parent].
     */
    fun newNestedDir(parent: DirHandle, name: String): DirHandle {
        val dir = parent as FakeDir
        val childDir = FakeDir(name)
        roots += childDir
        children.getOrPut(childDir) { mutableListOf() }
        val entry = FakeSubdirEntry(name, childDir)
        children.getOrPut(dir) { mutableListOf() } += entry
        return childDir
    }

    /**
     * Registers [name] as a child of [parent] with no content. Use
     * [newFile] with [content] when the test needs to exercise magic-byte
     * sniffing.
     */
    fun newFile(parent: DirHandle, name: String): EntryHandle =
        newFile(parent, name, ByteArray(0))

    /**
     * Registers [name] as a child of [parent] with [content] as its bytes.
     * Does not enforce uniqueness — two files with the same name under the
     * same parent are allowed to let tests model duplicate-name scenarios.
     */
    fun newFile(parent: DirHandle, name: String, content: ByteArray): EntryHandle {
        val dir = parent as FakeDir
        val entry = FakeFileEntry(name, content)
        children.getOrPut(dir) { mutableListOf() } += entry
        return entry
    }

    override fun listChildren(dir: DirHandle): List<EntryHandle> {
        val fakeDir = dir as FakeDir
        return children[fakeDir].orEmpty().toList()
    }

    override fun isDirectory(entry: EntryHandle): Boolean {
        return entry is FakeSubdirEntry
    }

    override fun readMagicBytes(entry: EntryHandle, len: Int): ByteArray {
        require(len >= 0) { "len must be non-negative, got $len" }
        val file = entry as FakeFileEntry
        return file.content.copyOf(minOf(len, file.content.size))
    }

    override fun dirHandle(entry: EntryHandle): DirHandle? {
        return if (entry is FakeSubdirEntry) entry.asDir else null
    }
}