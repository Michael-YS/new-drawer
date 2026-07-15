package com.drawer.v2.scanner

import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.PhotoCandidate
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.SuppressionStore
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.StorageGateway
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface ScanEvent {
    data class PhotoDiscovered(val photo: PhotoCandidate) : ScanEvent
    data class DirectoryScanned(val directory: StorageRef) : ScanEvent
    data class Problem(val location: StorageRef, val detail: String) : ScanEvent
    data object Completed : ScanEvent
}

/**
 * A cold, cancellable depth-first scanner. Exact excluded directory references
 * are never traversed, which keeps the target and `Drawer Trash` out of the
 * source queue when they sit under a source root.
 */
fun scanImages(
    roots: List<SourceRoot>,
    excludedDirectories: Set<StorageRef>,
    storage: StorageGateway,
    suppressionStore: SuppressionStore,
): Flow<ScanEvent> = flow {
    roots.filter { it.available }.forEach { root ->
        val directories = ArrayDeque<StorageRef>()
        directories.add(root.directory)
        while (directories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = directories.removeLast()
            if (directory in excludedDirectories) continue

            val children = try {
                storage.listChildren(directory)
            } catch (error: Throwable) {
                emit(ScanEvent.Problem(directory, error.message ?: "无法读取目录"))
                continue
            }

            children.forEach { entry ->
                currentCoroutineContext().ensureActive()
                when (entry.kind) {
                    StorageEntryKind.DIRECTORY -> if (entry.ref !in excludedDirectories) directories.add(entry.ref)
                    StorageEntryKind.OTHER -> Unit
                    StorageEntryKind.FILE -> {
                        val metadata = runCatching { storage.metadata(entry.ref) }.getOrNull() ?: entry.metadata
                        if (metadata == null) {
                            emit(ScanEvent.Problem(entry.ref, "无法读取文件元信息"))
                            return@forEach
                        }
                        val prefix = runCatching { storage.readPrefix(entry.ref, IMAGE_SIGNATURE_BYTES) }
                            .getOrElse {
                                emit(ScanEvent.Problem(entry.ref, it.message ?: "无法读取文件"))
                                return@forEach
                            }
                        if (!isSupportedImage(prefix)) return@forEach

                        val fingerprint = FileFingerprint(metadata.sizeBytes, metadata.modifiedAtEpochMs)
                        if (suppressionStore.isSuppressed(root.id, entry.ref, fingerprint)) return@forEach
                        emit(
                            ScanEvent.PhotoDiscovered(
                                PhotoCandidate(
                                    sourceRootId = root.id,
                                    file = entry.ref,
                                    parent = entry.parent,
                                    metadata = metadata,
                                ),
                            ),
                        )
                    }
                }
            }
            emit(ScanEvent.DirectoryScanned(directory))
        }
    }
    emit(ScanEvent.Completed)
}

private const val IMAGE_SIGNATURE_BYTES = 32
