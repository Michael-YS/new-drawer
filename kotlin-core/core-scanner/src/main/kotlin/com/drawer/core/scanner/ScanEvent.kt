package com.drawer.core.scanner

/**
 * Events emitted by [scanImagesFlow] as it walks a directory tree.
 *
 * Use [ScanEvent.ImageFound] to count discovered photos and feed them to
 * downstream consumers; [ScanEvent.DirScanned] for progress reporting;
 * [ScanEvent.Completed] as the terminal signal that no more events will
 * arrive for this run.
 */
sealed class ScanEvent {

    /**
     * An image file was discovered. [entry] is the same [EntryHandle] that
     * [FileSystemGateway.listChildren] returned; identity is preserved so
     * downstream consumers can store it without re-listing.
     */
    data class ImageFound(val entry: EntryHandle) : ScanEvent()

    /**
     * A directory was fully scanned. Emitted once per directory after all
     * of its direct children have been processed. The [dir] handle is the
     * same one passed in via the recursion stack.
     */
    data class DirScanned(val dir: DirHandle) : ScanEvent()

    /**
     * The terminal event for a scan run. Always emitted last, exactly once
     * per scan. Use it to close cursors, release resources, or flip UI
     * state from "scanning" to "done".
     */
    data object Completed : ScanEvent()
}