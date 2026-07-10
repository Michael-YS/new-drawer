package com.drawer.core.scanner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Walks [root] depth-first and emits [ScanEvent]s as it goes.
 *
 * Each directory is fully processed (all children scanned) before the flow
 * moves to the next directory, mirroring a stack-based DFS. Cancellation of
 * the collecting coroutine stops the scan cooperatively — no orphan walks
 * left behind.
 *
 * The algorithm depends only on [FileSystemGateway]; it never imports any
 * platform IO package. Tests can drive it against [FakeFileSystemGateway]
 * without touching the disk.
 *
 * @param root starting directory for the walk
 * @param gateway platform-agnostic filesystem surface used to list dirs
 *                and read magic bytes
 * @return cold [Flow] of [ScanEvent.ImageFound] / [ScanEvent.DirScanned] /
 *         [ScanEvent.Completed]
 */
fun scanImagesFlow(root: DirHandle, gateway: FileSystemGateway): Flow<ScanEvent> = flow {
    val stack = ArrayDeque<DirHandle>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val dir = stack.removeLast()
        for (entry in gateway.listChildren(dir)) {
            if (gateway.isDirectory(entry)) {
                // Recursion into nested dirs requires treating an entry as a
                // DirHandle. The gateway supplies this conversion via its
                // own open-as-dir semantics in production impls.
                stack.addLast(entry as DirHandle)
            } else if (isImageFile(entry, gateway)) {
                emit(ScanEvent.ImageFound(entry))
            }
        }
        emit(ScanEvent.DirScanned(dir))
    }
    emit(ScanEvent.Completed)
}