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
     * responsible for recursing into subdirectories if needed. Each entry
     * may be a file or a directory; call [isDirectory] to distinguish, and
     * call [dirHandle] to recurse into a directory entry.
     *
     * @return entries in the order the platform reports them; may be empty.
     */
    fun listChildren(dir: DirHandle): List<EntryHandle>

    /**
     * Reports whether [entry] is a directory.
     *
     * Used by traversal code after [listChildren] returns a polymorphic
     * [EntryHandle] to decide whether to recurse or to hand the entry to
     * the image filter.
     */
    fun isDirectory(entry: EntryHandle): Boolean

    /**
     * Reads up to [len] bytes from the start of [entry].
     *
     * Used by [isImageFile] to sniff magic bytes — extension matching is
     * unreliable because users rename files. Returns fewer than [len]
     * bytes when the entry is shorter than [len]; returns an empty array
     * for an empty entry.
     *
     * Behavior for directories is unspecified; callers should branch on
     * [isDirectory] first.
     */
    fun readMagicBytes(entry: EntryHandle, len: Int): ByteArray

    /**
     * Promotes a directory [entry] (one where [isDirectory] returned true)
     * to a [DirHandle] so traversal can pass it back into [listChildren].
     *
     * Returns `null` for non-directory entries. Production impls return the
     * underlying native handle (NIO `Path`, SAF `DocumentFile`) wrapped as
     * a [DirHandle]; the fake returns the entry itself when it already
     * implements both interfaces.
     */
    fun dirHandle(entry: EntryHandle): DirHandle?
}