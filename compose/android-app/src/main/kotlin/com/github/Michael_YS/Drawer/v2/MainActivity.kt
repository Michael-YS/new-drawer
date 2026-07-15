package com.github.Michael_YS.Drawer.v2

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.PhotoCandidate
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.SuppressedItem
import com.drawer.v2.domain.SuppressionReason
import com.drawer.v2.domain.TargetRoot
import com.drawer.v2.persistence.SqlDelightConfigurationStore
import com.drawer.v2.persistence.SqlDelightOperationJournalStore
import com.drawer.v2.persistence.SqlDelightSuppressionStore
import com.drawer.v2.persistence.createAndroidDrawerDatabase
import com.drawer.v2.scanner.ScanEvent
import com.drawer.v2.scanner.scanImages
import com.drawer.v2.storage.StorageEntry
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.StorageGateway
import com.drawer.v2.storage.TrashSummary
import com.drawer.v2.storage.clearDirectoryContents
import com.drawer.v2.storage.saf.SafStorageGateway
import com.drawer.v2.storage.summarizeDirectory
import com.drawer.v2.transaction.ExistingTargetToTrash
import com.drawer.v2.transaction.SafeMove
import com.drawer.v2.transaction.SafeMoveRequest
import com.drawer.v2.transaction.SessionUndo
import com.drawer.v2.transaction.UndoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AndroidDrawerApp() }
    }
}

private data class AndroidConflict(
    val candidate: PhotoCandidate,
    val category: String,
    val categoryDirectory: StorageRef,
    val existing: StorageEntry,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AndroidDrawerApp() {
    val context = LocalContext.current
    val storage = remember { SafStorageGateway(context) }
    val database = remember { createAndroidDrawerDatabase(context) }
    val configuration = remember { SqlDelightConfigurationStore(database) }
    val journal = remember { SqlDelightOperationJournalStore(database) }
    val suppressions = remember { SqlDelightSuppressionStore(database) }
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf(configuration.sourceRoots()) }
    var target by remember { mutableStateOf(configuration.targetRoot()) }
    var photos by remember { mutableStateOf(emptyList<PhotoCandidate>()) }
    var categories by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf("Grant a source and target directory to begin.") }
    var recoveryChecked by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var conflict by remember { mutableStateOf<AndroidConflict?>(null) }
    var confirmOverwrite by remember { mutableStateOf<AndroidConflict?>(null) }
    var renameConflict by remember { mutableStateOf<AndroidConflict?>(null) }
    var pendingSourceDelete by remember { mutableStateOf<PhotoCandidate?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var clearTrashConfirmationOpen by remember { mutableStateOf(false) }
    var trashSummary by remember { mutableStateOf(TrashSummary()) }
    var undoStack by remember { mutableStateOf(emptyList<SessionUndo>()) }
    var renameTo by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }

    fun persistGrant(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun refreshCategories() {
        val configuredTarget = target ?: return
        scope.launch {
            categories = withContext(Dispatchers.IO) {
                val directories = storage.listChildren(configuredTarget.directory)
                    .filter { it.kind == StorageEntryKind.DIRECTORY && it.name != TRASH_DIRECTORY }
                directories.forEach { configuration.categoryFirstSeen(configuredTarget, it.name, System.currentTimeMillis()) }
                configuration.categoryNames(configuredTarget)
            }
        }
    }

    fun scan() {
        val configuredTarget = target ?: return
        if (sources.isEmpty()) return
        if (sources.any { storage.isSameOrDescendant(it.directory, configuredTarget.directory) }) {
            status = "A source directory cannot be the target directory or one of its children."
            return
        }
        val effectiveSources = sources.filter { source ->
            sources.none { other ->
                other != source && storage.isSameOrDescendant(source.directory, other.directory)
            }
        }
        scanJob?.cancel()
        photos = emptyList()
        status = "Scanning in the background…"
        refreshCategories()
        scanJob = scope.launch {
            scanImages(
                roots = effectiveSources,
                excludedDirectories = setOf(configuredTarget.directory),
                storage = storage,
                suppressionStore = suppressions,
            ).flowOn(Dispatchers.IO).collect { event ->
                when (event) {
                    is ScanEvent.PhotoDiscovered -> photos = (photos + event.photo)
                        .sortedByDescending { it.metadata.modifiedAtEpochMs }
                    is ScanEvent.Problem -> status = "Scan issue: ${event.detail}"
                    ScanEvent.Completed -> status = if (photos.isEmpty()) "Scan complete. No pending photos." else "Scan complete."
                    is ScanEvent.DirectoryScanned -> Unit
                }
            }
        }
    }

    fun skip(candidate: PhotoCandidate) {
        scope.launch {
            withContext(Dispatchers.IO) {
                suppressions.suppress(
                    SuppressedItem(
                        candidate.sourceRootId,
                        candidate.file,
                        FileFingerprint(candidate.metadata.sizeBytes, candidate.metadata.modifiedAtEpochMs),
                        SuppressionReason.SKIPPED,
                    ),
                )
            }
            photos = photos - candidate
        }
    }

    fun move(candidate: PhotoCandidate, category: String, destinationName: String = candidate.metadata.name, overwrite: StorageEntry? = null) {
        val configuredTarget = target ?: return
        scope.launch {
            try {
                val categoryDirectory = withContext(Dispatchers.IO) {
                    storage.ensureDirectory(configuredTarget.directory, category)
                }
                val collision = withContext(Dispatchers.IO) {
                    storage.findChild(categoryDirectory, destinationName)
                }
                if (collision != null && collision.ref != overwrite?.ref) {
                    conflict = AndroidConflict(candidate, category, categoryDirectory, collision)
                    return@launch
                }
                if (collision?.kind == StorageEntryKind.DIRECTORY) {
                    status = "Cannot overwrite a directory. Rename the photo instead."
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    val overwritePlan = collision?.let { existing ->
                        val trash = storage.ensureDirectory(configuredTarget.directory, TRASH_DIRECTORY)
                        ExistingTargetToTrash(existing.ref, trash, uniqueTrashName(storage, trash, existing.name))
                    }
                    SafeMove(storage, journal).execute(
                        SafeMoveRequest(
                            operationId = UUID.randomUUID().toString(),
                            source = candidate.file,
                            sourceParent = candidate.parent,
                            sourceName = candidate.metadata.name,
                            targetParent = categoryDirectory,
                            targetName = destinationName,
                            overwrite = overwritePlan,
                        ),
                    )
                }
                status = when (result) {
                    is com.drawer.v2.transaction.SafeMoveResult.Completed -> {
                        photos = photos - candidate
                        undoStack = undoStack + result.undo
                        "Moved to $category."
                    }
                    is com.drawer.v2.transaction.SafeMoveResult.SourceDeleteFailed -> {
                        pendingSourceDelete = candidate
                        "Copy completed, but the source remains. Choose how to resolve it."
                    }
                }
            } catch (error: Throwable) {
                status = "Move failed: ${error.message ?: error::class.simpleName}"
            }
        }
    }

    fun undoLastMove() {
        val latest = undoStack.lastOrNull() ?: return
        scope.launch {
            status = "Undoing the last move..."
            when (val result = withContext(Dispatchers.IO) { SafeMove(storage, journal).undo(latest) }) {
                UndoResult.Completed -> {
                    undoStack = undoStack.dropLast(1)
                    status = "Last move undone."
                    scan()
                }
                is UndoResult.NeedsUserIntervention -> status = "Undo needs manual attention: ${result.detail}"
            }
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            require(storage.isSupportedLocalTree(uri)) { "Only local and SD-card folders are supported." }
            val ref = storage.directoryFromTreeUri(uri)
            SourceRoot(UUID.randomUUID().toString(), ref, uri.lastPathSegment ?: "Source", true)
        }.onSuccess { root ->
            if (target != null && storage.isSameOrDescendant(root.directory, target!!.directory)) {
                status = "A source directory cannot be inside the target directory."
            } else if (sources.any { storage.isSameOrDescendant(root.directory, it.directory) }) {
                // A selected parent already includes this directory.
                status = "That source is already covered by a selected parent directory."
            } else {
                runCatching { persistGrant(uri) }.onFailure {
                    status = it.message ?: "Could not retain source permission."
                    return@onSuccess
                }
                val nestedRoots = sources.filter { storage.isSameOrDescendant(it.directory, root.directory) }
                nestedRoots.forEach { configuration.removeSourceRoot(it.id) }
                sources = sources - nestedRoots
                configuration.saveSourceRoot(root)
                sources = sources + root
            }
        }.onFailure { status = it.message ?: "Could not add source directory." }
    }
    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            require(storage.isSupportedLocalTree(uri)) { "Only local and SD-card folders are supported." }
            TargetRoot(storage.directoryFromTreeUri(uri), uri.lastPathSegment ?: "Target")
        }.onSuccess { chosen ->
            if (sources.any { storage.isSameOrDescendant(it.directory, chosen.directory) }) {
                status = "The target cannot contain an existing source directory."
            } else {
                runCatching { persistGrant(uri) }.onFailure {
                    status = it.message ?: "Could not retain target permission."
                    return@onSuccess
                }
                target = chosen
                configuration.saveTargetRoot(chosen)
            }
        }.onFailure { status = it.message ?: "Could not select target directory." }
    }

    LaunchedEffect(Unit) {
        val recovery = withContext(Dispatchers.IO) { SafeMove(storage, journal).recover() }
        status = when (recovery) {
            is com.drawer.v2.transaction.RecoveryResult.NoPendingOperation -> status
            is com.drawer.v2.transaction.RecoveryResult.Completed -> "Recovered the previous move."
            is com.drawer.v2.transaction.RecoveryResult.RolledBackTemporary -> "Recovered an incomplete move."
            is com.drawer.v2.transaction.RecoveryResult.NeedsUserIntervention ->
                "A previous file operation needs manual attention before continuing."
        }
        recoveryChecked = recovery !is com.drawer.v2.transaction.RecoveryResult.NeedsUserIntervention
    }
    LaunchedEffect(sources, target, recoveryChecked) { if (recoveryChecked) scan() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Drawer v2", style = MaterialTheme.typography.headlineMedium)
                Text(status)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sourcePicker.launch(null) }) { Text("Add source") }
                    OutlinedButton(onClick = { targetPicker.launch(null) }) { Text("Choose target") }
                    OutlinedButton(onClick = {
                        settingsOpen = true
                        scope.launch { trashSummary = withContext(Dispatchers.IO) { readTrashSummary(storage, target) } }
                    }) { Text("Settings") }
                    OutlinedButton(
                        enabled = undoStack.isNotEmpty(),
                        onClick = ::undoLastMove,
                    ) { Text("Undo last move") }
                }
                Text("Target: ${target?.displayName ?: "not selected"}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sources.forEach { source ->
                        FilterChip(selected = true, onClick = {
                            configuration.removeSourceRoot(source.id)
                            sources = sources - source
                        }, label = { Text("${source.displayName} ×") })
                    }
                }
                AndroidPhoto(photos.firstOrNull(), Modifier.height(260.dp).fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        Button(
                            enabled = pendingSourceDelete == null,
                            onClick = { photos.firstOrNull()?.let { move(it, category) } },
                        ) { Text(category) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCategory, { newCategory = it }, label = { Text("New category") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val name = newCategory.trim()
                        val configuredTarget = target
                        if (name.isNotEmpty() && configuredTarget != null) scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { storage.ensureDirectory(configuredTarget.directory, name) }
                            }
                                .onSuccess {
                                    configuration.categoryFirstSeen(configuredTarget, name, System.currentTimeMillis())
                                    categories = configuration.categoryNames(configuredTarget)
                                    newCategory = ""
                                }
                                .onFailure { status = "Invalid category: ${it.message}" }
                        }
                    }) { Text("Create") }
                }
                OutlinedButton(
                    enabled = pendingSourceDelete == null,
                    onClick = { photos.firstOrNull()?.let(::skip) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip for now") }
            }
        }
    }

    conflict?.let { current ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("A photo with this name already exists") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Incoming: ${current.candidate.metadata.name}, ${current.candidate.metadata.sizeBytes} bytes")
                    AndroidImage(current.candidate.file, Modifier.height(120.dp).fillMaxWidth())
                    Text(current.existing.metadata?.let { "Existing: ${it.name}, ${it.sizeBytes} bytes, modified ${it.modifiedAtEpochMs}" } ?: "Existing metadata unavailable")
                    AndroidImage(current.existing.ref, Modifier.height(120.dp).fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { confirmOverwrite = current; conflict = null }) { Text("Overwrite") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { skip(current.candidate); conflict = null }) { Text("Skip") }
                    OutlinedButton(onClick = { renameTo = suggestedName(current.candidate.metadata.name); renameConflict = current; conflict = null }) { Text("Rename") }
                }
            },
        )
    }
    renameConflict?.let { current ->
        AlertDialog(
            onDismissRequest = { renameConflict = null },
            title = { Text("Rename incoming photo") },
            text = { OutlinedTextField(renameTo, { renameTo = it }, label = { Text("File name") }) },
            confirmButton = { Button(onClick = { move(current.candidate, current.category, renameTo); renameConflict = null }) { Text("Move") } },
            dismissButton = { OutlinedButton(onClick = { renameConflict = null }) { Text("Cancel") } },
        )
    }
    confirmOverwrite?.let { current ->
        var checked by remember(current) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmOverwrite = null },
            title = { Text("Move existing photo to Drawer Trash?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { checked = it })
                    Text("I understand the existing target is moved to Drawer Trash.")
                }
            },
            confirmButton = { Button(enabled = checked, onClick = { move(current.candidate, current.category, overwrite = current.existing); confirmOverwrite = null }) { Text("Confirm overwrite") } },
            dismissButton = { OutlinedButton(onClick = { confirmOverwrite = null }) { Text("Cancel") } },
        )
    }
    pendingSourceDelete?.let { candidate ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("The source photo could not be deleted") },
            text = { Text("The copied target is present and the original remains. Resolve this before another move.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        when (withContext(Dispatchers.IO) { SafeMove(storage, journal).recover() }) {
                            is com.drawer.v2.transaction.RecoveryResult.Completed -> {
                                photos = photos - candidate
                                pendingSourceDelete = null
                                status = "Source deletion recovered."
                            }
                            else -> status = "Retry could not delete the source."
                        }
                    }
                }) { Text("Retry deletion") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            when (withContext(Dispatchers.IO) { SafeMove(storage, journal).rollbackPendingSourceDelete() }) {
                                is com.drawer.v2.transaction.RecoveryResult.RolledBackTemporary -> {
                                    pendingSourceDelete = null
                                    status = "Move rolled back; the original remains in place."
                                }
                                else -> status = "Rollback needs manual attention."
                            }
                        }
                    }) { Text("Safely roll back") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                suppressions.suppress(
                                    SuppressedItem(
                                        candidate.sourceRootId,
                                        candidate.file,
                                        FileFingerprint(candidate.metadata.sizeBytes, candidate.metadata.modifiedAtEpochMs),
                                        SuppressionReason.KEPT_COPY,
                                    ),
                                )
                                journal.clear()
                            }
                            photos = photos - candidate
                            pendingSourceDelete = null
                            status = "Kept both copies; the source is marked as handled."
                        }
                    }) { Text("Keep both") }
                }
            },
        )
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Skipped and kept-copy records survive restarts until cleared.")
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { suppressions.clear(SuppressionReason.SKIPPED) }
                            status = "Cleared skipped items; scanning again."
                            scan()
                        }
                    }) { Text("Clear skipped items") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { suppressions.clear(SuppressionReason.KEPT_COPY) }
                            status = "Kept copies will be checked again."
                            scan()
                        }
                    }) { Text("Recheck kept copies") }
                    Text("Drawer Trash: ${trashSummary.fileCount} files, ${formatBytes(trashSummary.totalBytes)}")
                    OutlinedButton(
                        enabled = trashSummary.fileCount > 0,
                        onClick = { clearTrashConfirmationOpen = true },
                    ) { Text("Empty Drawer Trash") }
                }
            },
            confirmButton = { Button(onClick = { settingsOpen = false }) { Text("Done") } },
        )
    }
    if (clearTrashConfirmationOpen) {
        var acknowledged by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { clearTrashConfirmationOpen = false },
            title = { Text("Permanently empty Drawer Trash?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently deletes ${trashSummary.fileCount} files (${formatBytes(trashSummary.totalBytes)}). It cannot be undone.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text("I understand these files will be permanently deleted.")
                    }
                }
            },
            confirmButton = {
                Button(enabled = acknowledged, onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { emptyTrash(storage, target) }
                        }.onSuccess { deleted ->
                            trashSummary = TrashSummary()
                            status = "Permanently deleted ${deleted.fileCount} files from Drawer Trash."
                            clearTrashConfirmationOpen = false
                        }.onFailure { status = "Could not empty Drawer Trash: ${it.message}" }
                    }
                }) { Text("Permanently delete") }
            },
            dismissButton = { OutlinedButton(onClick = { clearTrashConfirmationOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AndroidPhoto(photo: PhotoCandidate?, modifier: Modifier = Modifier) {
    if (photo == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("No pending photo") }
    } else {
        Column {
            AndroidImage(photo.file, modifier)
            Text("${photo.metadata.name} • ${photo.metadata.sizeBytes} bytes • modified ${photo.metadata.modifiedAtEpochMs}")
        }
    }
}

@Composable
private fun AndroidImage(ref: StorageRef, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, ref) {
        value = withContext(Dispatchers.IO) { decodePreview(context, ref) }
    }
    val image = bitmap
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("Preview unavailable") }
    } else {
        Image(image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private fun decodePreview(context: android.content.Context, ref: StorageRef): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val documentUri = Uri.parse(ref.token.substringAfter('\n'))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(documentUri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (bounds.outWidth / sample > MAX_PREVIEW_EDGE || bounds.outHeight / sample > MAX_PREVIEW_EDGE) sample *= 2
            inSampleSize = sample
        }
        context.contentResolver.openInputStream(documentUri).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }.getOrNull()

private suspend fun uniqueTrashName(storage: SafStorageGateway, trash: StorageRef, original: String): String {
    if (storage.findChild(trash, original) == null) return original
    val dot = original.lastIndexOf('.')
    val stem = if (dot > 0) original.substring(0, dot) else original
    val extension = if (dot > 0) original.substring(dot) else ""
    var index = 1
    while (storage.findChild(trash, "$stem ($index)$extension") != null) index++
    return "$stem ($index)$extension"
}

private fun suggestedName(original: String): String {
    val dot = original.lastIndexOf('.')
    val stem = if (dot > 0) original.substring(0, dot) else original
    val extension = if (dot > 0) original.substring(dot) else ""
    return "$stem (1)$extension"
}

private suspend fun readTrashSummary(storage: StorageGateway, target: TargetRoot?): TrashSummary {
    val root = target ?: return TrashSummary()
    val trash = storage.findChild(root.directory, TRASH_DIRECTORY) ?: return TrashSummary()
    return if (trash.kind == StorageEntryKind.DIRECTORY) summarizeDirectory(storage, trash.ref) else TrashSummary()
}

private suspend fun emptyTrash(storage: StorageGateway, target: TargetRoot?): TrashSummary {
    val root = target ?: return TrashSummary()
    val trash = storage.findChild(root.directory, TRASH_DIRECTORY) ?: return TrashSummary()
    return if (trash.kind == StorageEntryKind.DIRECTORY) clearDirectoryContents(storage, trash.ref) else TrashSummary()
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KiB".format(bytes / 1_024.0)
    bytes < 1_024 * 1_024 * 1_024 -> "%.1f MiB".format(bytes / (1_024.0 * 1_024.0))
    else -> "%.1f GiB".format(bytes / (1_024.0 * 1_024.0 * 1_024.0))
}

private const val TRASH_DIRECTORY = "Drawer Trash"
private const val MAX_PREVIEW_EDGE = 1600
