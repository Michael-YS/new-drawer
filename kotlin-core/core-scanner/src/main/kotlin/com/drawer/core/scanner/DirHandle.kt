package com.drawer.core.scanner

/**
 * Opaque handle to a directory in a [FileSystemGateway].
 *
 * Implementations decide the underlying representation: an absolute path on
 * Windows (NIO), a SAF tree URI plus relative path on Android. Callers must
 * never inspect or persist the underlying value directly; treat instances as
 * tokens that only the originating gateway can interpret.
 *
 * Equality is identity-based; two handles from different gateways are never
 * equal even if they point to the same physical directory.
 */
interface DirHandle