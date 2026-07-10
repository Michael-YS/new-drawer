package com.drawer.core.scanner

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

    fun newDir(name: String): DirHandle {
        val dir = FakeDir(name)
        roots += dir
        children.getOrPut(dir) { mutableListOf() }
        return dir
    }

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