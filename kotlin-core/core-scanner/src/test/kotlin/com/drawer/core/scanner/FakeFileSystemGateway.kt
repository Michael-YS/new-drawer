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
        val name: String = overrideName
        override fun toString(): String = "FakeDir($name)"
    }

    private inner class FakeEntry(overrideName: String) : EntryHandle {
        override val name: String = overrideName
        override fun toString(): String = "FakeEntry($name)"
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
     * Registers [name] as a child of [parent]. Does not enforce uniqueness
     * — two files with the same name under the same parent are allowed to
     * let tests model duplicate-name scenarios.
     */
    fun newFile(parent: DirHandle, name: String): EntryHandle {
        val dir = parent as FakeDir
        val entry = FakeEntry(name)
        children.getOrPut(dir) { mutableListOf() } += entry
        return entry
    }

    override fun listChildren(dir: DirHandle): List<EntryHandle> {
        val fakeDir = dir as FakeDir
        return children[fakeDir].orEmpty().toList()
    }
}