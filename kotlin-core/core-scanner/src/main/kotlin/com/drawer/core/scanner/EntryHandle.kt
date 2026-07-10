package com.drawer.core.scanner

/**
 * Opaque handle to a filesystem entry (file or directory) in a
 * [FileSystemGateway].
 *
 * Same identity rules as [DirHandle]: callers must not inspect or persist
 * the underlying representation. Equality is identity-based.
 */
interface EntryHandle {
    /**
     * Filename component as seen by the user, with no path separators.
     *
     * Safe to display in the UI and to use for log lines. Not safe to use as
     * a primary key across rescans or scope changes; use the [EntryHandle]
     * itself for identity.
     */
    val name: String
}