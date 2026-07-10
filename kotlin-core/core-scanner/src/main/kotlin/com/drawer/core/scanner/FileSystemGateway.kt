package com.drawer.core.scanner

/**
 * Platform-agnostic interface for filesystem operations used by the scanner
 * and file-ops modules.
 *
 * Lets traversal, filtering, and move/copy logic live in pure Kotlin that
 * never imports `java.nio.file.*`, `android.*`, or any UI framework. Each
 * platform provides its own implementation: `NioFileSystemGateway` on
 * Windows, `SafFileSystemGateway` on Android. Tests use
 * [FakeFileSystemGateway] backed by an in-memory map.
 *
 * All methods are synchronous; callers wrap them in coroutines when needed.
 * Implementations are expected to be safe to call from a single coroutine at
 * a time per instance.
 */
interface FileSystemGateway {
    /**
     * Lists the immediate children of [dir]. Order is implementation-defined.
     *
     * Returns only direct children, not descendants. The caller is
     * responsible for recursing into subdirectories if needed.
     *
     * @return entries in the order the platform reports them; may be empty.
     */
    fun listChildren(dir: DirHandle): List<EntryHandle>
}