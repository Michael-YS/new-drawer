package com.drawer.core.scanner

interface FileSystemGateway {
    fun listChildren(dir: DirHandle): List<EntryHandle>
}