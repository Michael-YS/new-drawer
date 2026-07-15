package com.drawer.v2.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.PhotoCandidate
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.SuppressedItem
import com.drawer.v2.domain.SuppressionReason
import com.drawer.v2.domain.TargetRoot
import com.drawer.v2.persistence.SqlDelightConfigurationStore
import com.drawer.v2.persistence.SqlDelightOperationJournalStore
import com.drawer.v2.persistence.SqlDelightSuppressionStore
import com.drawer.v2.persistence.createDesktopDrawerDatabase
import com.drawer.v2.scanner.ScanEvent
import com.drawer.v2.scanner.scanImages
import com.drawer.v2.storage.StorageEntry
import com.drawer.v2.storage.StorageEntryKind
import com.drawer.v2.storage.StorageGateway
import com.drawer.v2.storage.TrashSummary
import com.drawer.v2.storage.clearDirectoryContents
import com.drawer.v2.storage.nio.NioStorageGateway
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
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Path
import java.util.UUID
import javax.swing.JFileChooser

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Drawer v2",
    ) {
        DesktopDrawerApp()
    }
}

private data class Conflict(
    val candidate: PhotoCandidate,
    val category: String,
    val categoryDirectory: com.drawer.v2.domain.StorageRef,
    val existing: StorageEntry,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DesktopDrawerApp() {
    val storage = remember { NioStorageGateway() }
    val database = remember {
        createDesktopDrawerDatabase(File(System.getProperty("user.home"), ".drawer-v2/drawer-v2.db"))
    }
    val configuration = remember { SqlDelightConfigurationStore(database) }
    val journal = remember { SqlDelightOperationJournalStore(database) }
    val suppressions = remember { SqlDelightSuppressionStore(database) }
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf(configuration.sourceRoots()) }
    var target by remember { mutableStateOf(configuration.targetRoot()) }
    var photos by remember { mutableStateOf(emptyList<PhotoCandidate>()) }
    var categories by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf("Choose at least one source and one target directory.") }
    var recoveryChecked by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var conflict by remember { mutableStateOf<Conflict?>(null) }
    var confirmOverwrite by remember { mutableStateOf<Conflict?>(null) }
    var renameConflict by remember { mutableStateOf<Conflict?>(null) }
    var pendingSourceDelete by remember { mutableStateOf<PhotoCandidate?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var clearTrashConfirmationOpen by remember { mutableStateOf(false) }
    var trashSummary by remember { mutableStateOf(TrashSummary()) }
    var undoStack by remember { mutableStateOf(emptyList<SessionUndo>()) }
    var pendingDirectoryChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    var renameTo by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }

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
        val configuredTarget = target
        if (sources.isEmpty() || configuredTarget == null) return
        val targetPath = Path.of(configuredTarget.directory.token).toAbsolutePath().normalize()
        if (sources.any { Path.of(it.directory.token).toAbsolutePath().normalize().startsWith(targetPath) }) {
            status = "A source directory cannot be the target directory or one of its children."
            return
        }
        // A selected child is already included by its selected parent. Keep the
        // parent only, matching the documented DCIM/Camera overlap behaviour.
        val effectiveSources = sources.filter { source ->
            val sourcePath = Path.of(source.directory.token).toAbsolutePath().normalize()
            sources.none { other ->
                other != source && sourcePath.startsWith(Path.of(other.directory.token).toAbsolutePath().normalize())
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
                        sourceRootId = candidate.sourceRootId,
                        file = candidate.file,
                        fingerprint = FileFingerprint(candidate.metadata.sizeBytes, candidate.metadata.modifiedAtEpochMs),
                        reason = SuppressionReason.SKIPPED,
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
                    conflict = Conflict(candidate, category, categoryDirectory, collision)
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

    fun requestDirectoryChange(change: () -> Unit) {
        if (scanJob?.isActive == true) pendingDirectoryChange = change else change()
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
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Drawer v2", style = MaterialTheme.typography.headlineMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium)
                DirectoryControls(
                    sources = sources,
                    target = target,
                    onAddSources = {
                        chooseDirectories().map { path ->
                            SourceRoot(UUID.randomUUID().toString(), storage.directory(path), path.fileName.toString(), true)
                        }.filter { newRoot -> sources.none { it.directory == newRoot.directory } }
                            .let { selected ->
                                val targetPath = target?.directory?.token?.let { Path.of(it).toAbsolutePath().normalize() }
                                val valid = selected.filter { root ->
                                    targetPath == null || !Path.of(root.directory.token).toAbsolutePath().normalize().startsWith(targetPath)
                                }
                                if (valid.size != selected.size) {
                                    status = "A source directory inside the target directory was ignored."
                                }
                                val effective = valid.filter { root ->
                                    val rootPath = Path.of(root.directory.token).toAbsolutePath().normalize()
                                    sources.none { existing -> rootPath.startsWith(Path.of(existing.directory.token).toAbsolutePath().normalize()) } &&
                                        valid.none { other ->
                                            other != root && rootPath.startsWith(Path.of(other.directory.token).toAbsolutePath().normalize())
                                        }
                                }
                                val replacedNestedSources = sources.filter { existing ->
                                    val existingPath = Path.of(existing.directory.token).toAbsolutePath().normalize()
                                    effective.any { root -> existingPath.startsWith(Path.of(root.directory.token).toAbsolutePath().normalize()) }
                                }
                                if (effective.isNotEmpty()) requestDirectoryChange {
                                    replacedNestedSources.forEach { configuration.removeSourceRoot(it.id) }
                                    effective.forEach(configuration::saveSourceRoot)
                                    sources = (sources - replacedNestedSources) + effective
                                }
                            }
                    },
                    onRemoveSource = { root ->
                        requestDirectoryChange {
                            configuration.removeSourceRoot(root.id)
                            sources = sources - root
                        }
                    },
                    onChooseTarget = {
                        chooseDirectory()?.let { path ->
                            val chosen = TargetRoot(storage.directory(path), path.fileName.toString())
                            val chosenPath = Path.of(chosen.directory.token).toAbsolutePath().normalize()
                            if (sources.any { Path.of(it.directory.token).toAbsolutePath().normalize().startsWith(chosenPath) }) {
                                status = "The target cannot contain an existing source directory."
                            } else {
                                requestDirectoryChange {
                                    target = chosen
                                    configuration.saveTargetRoot(chosen)
                                }
                            }
                        }
                    },
                    onSettings = {
                        settingsOpen = true
                        scope.launch { trashSummary = withContext(Dispatchers.IO) { readTrashSummary(storage, configuration.trashRoots()) } }
                    },
                    onUndo = if (undoStack.isNotEmpty()) ::undoLastMove else null,
                )
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PhotoPanel(photos.firstOrNull(), Modifier.weight(1f))
                    CategoryPanel(
                        categories = categories,
                        newCategory = newCategory,
                        onNewCategoryChanged = { newCategory = it },
                        onCreate = {
                            val name = newCategory.trim()
                            if (name.isNotEmpty()) {
                                val configuredTarget = target
                                if (configuredTarget != null) {
                                    scope.launch {
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
                                }
                            }
                        },
                        movesEnabled = pendingSourceDelete == null,
                        onCategory = { photos.firstOrNull()?.let { candidate -> move(candidate, it) } },
                        onSkip = { photos.firstOrNull()?.let(::skip) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    conflict?.let { current ->
        ConflictDialog(
            conflict = current,
            onSkip = { skip(current.candidate); conflict = null },
            onRename = {
                renameTo = suggestedName(current.candidate.metadata.name)
                renameConflict = current
                conflict = null
            },
            onOverwrite = { confirmOverwrite = current; conflict = null },
            onDismiss = { conflict = null },
        )
    }
    renameConflict?.let { current ->
        AlertDialog(
            onDismissRequest = { renameConflict = null },
            title = { Text("Rename incoming photo") },
            text = { OutlinedTextField(renameTo, { renameTo = it }, label = { Text("File name") }) },
            confirmButton = {
                Button(onClick = { move(current.candidate, current.category, renameTo); renameConflict = null }) { Text("Move") }
            },
            dismissButton = { OutlinedButton(onClick = { renameConflict = null }) { Text("Cancel") } },
        )
    }
    confirmOverwrite?.let { current ->
        var checked by remember(current) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmOverwrite = null },
            title = { Text("Move the existing photo to Drawer Trash?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { checked = it })
                    Text("I understand the existing target will be replaced and moved to Drawer Trash.")
                }
            },
            confirmButton = {
                Button(enabled = checked, onClick = { move(current.candidate, current.category, overwrite = current.existing); confirmOverwrite = null }) {
                    Text("Confirm overwrite")
                }
            },
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
                        when (SafeMove(storage, journal).recover()) {
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
                            when (SafeMove(storage, journal).rollbackPendingSourceDelete()) {
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
                            suppressions.suppress(
                                SuppressedItem(
                                    candidate.sourceRootId,
                                    candidate.file,
                                    FileFingerprint(candidate.metadata.sizeBytes, candidate.metadata.modifiedAtEpochMs),
                                    SuppressionReason.KEPT_COPY,
                                ),
                            )
                            journal.clear()
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
                            withContext(Dispatchers.IO) { emptyTrash(storage, configuration.trashRoots()) }
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
    pendingDirectoryChange?.let { change ->
        AlertDialog(
            onDismissRequest = { pendingDirectoryChange = null },
            title = { Text("Change directories while scanning?") },
            text = { Text("The current background scan will be interrupted, then the new directory selection will be used.") },
            confirmButton = {
                Button(onClick = {
                    scanJob?.cancel()
                    pendingDirectoryChange = null
                    change()
                }) { Text("Interrupt scan and change") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingDirectoryChange = null }) { Text("Keep current scan") } },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DirectoryControls(
    sources: List<SourceRoot>,
    target: TargetRoot?,
    onAddSources: () -> Unit,
    onRemoveSource: (SourceRoot) -> Unit,
    onChooseTarget: () -> Unit,
    onSettings: () -> Unit,
    onUndo: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAddSources) { Text("Add source directories") }
            OutlinedButton(onClick = onChooseTarget) { Text("Choose target directory") }
            OutlinedButton(onClick = onSettings) { Text("Settings") }
            OutlinedButton(enabled = onUndo != null, onClick = { onUndo?.invoke() }) { Text("Undo last move") }
            Text("Target: ${target?.displayName ?: "not selected"}")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { source ->
                FilterChip(selected = true, onClick = { onRemoveSource(source) }, label = { Text("${source.displayName} ×") })
            }
        }
    }
}

@Composable
private fun PhotoPanel(photo: PhotoCandidate?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), tonalElevation = 1.dp) {
        if (photo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending photo") }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopPhoto(photo.file.token, Modifier.weight(1f).fillMaxWidth())
                PhotoMetadata(photo)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CategoryPanel(
    categories: List<String>,
    newCategory: String,
    onNewCategoryChanged: (String) -> Unit,
    onCreate: () -> Unit,
    movesEnabled: Boolean,
    onCategory: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), tonalElevation = 1.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    Button(enabled = movesEnabled, onClick = { onCategory(category) }) { Text(category) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = onNewCategoryChanged,
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreate) { Text("Create") }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(enabled = movesEnabled, onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }
}

@Composable
private fun ConflictDialog(
    conflict: Conflict,
    onSkip: () -> Unit,
    onRename: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A photo with this name already exists") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Incoming")
                    DesktopPhoto(conflict.candidate.file.token, Modifier.height(140.dp).fillMaxWidth())
                    PhotoMetadata(conflict.candidate)
                }
                Column(Modifier.weight(1f)) {
                    Text("Existing")
                    DesktopPhoto(conflict.existing.ref.token, Modifier.height(140.dp).fillMaxWidth())
                    val metadata = conflict.existing.metadata
                    Text(metadata?.let(::metadataSummary) ?: "Metadata unavailable")
                }
            }
        },
        confirmButton = { Button(onClick = onOverwrite) { Text("Overwrite") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip) { Text("Skip") }
                OutlinedButton(onClick = onRename) { Text("Rename") }
            }
        },
    )
}

@Composable
private fun PhotoMetadata(photo: PhotoCandidate) {
    Text(metadataSummary(photo.metadata))
}

private fun metadataSummary(metadata: MediaMetadata): String = buildString {
    append(metadata.name)
    append("\n${metadata.sizeBytes} bytes")
    if (metadata.width != null && metadata.height != null) append("\n${metadata.width} × ${metadata.height}")
    metadata.createdAtEpochMs?.let { append("\nCreated: $it") }
    append("\nModified: ${metadata.modifiedAtEpochMs}")
}

@Composable
private fun DesktopPhoto(token: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, token) {
        value = withContext(Dispatchers.IO) {
            runCatching { Image.makeFromEncoded(File(token).readBytes()).toComposeImageBitmap() }.getOrNull()
        }
    }
    val image = bitmap
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("Preview unavailable")
        }
    } else {
        Image(image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private fun chooseDirectory(): Path? = chooseDirectories(multiple = false).firstOrNull()

private fun chooseDirectories(multiple: Boolean = true): List<Path> {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isMultiSelectionEnabled = multiple
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        if (multiple) chooser.selectedFiles.map { it.toPath() } else listOfNotNull(chooser.selectedFile?.toPath())
    } else {
        emptyList()
    }
}

private suspend fun uniqueTrashName(
    storage: NioStorageGateway,
    trash: com.drawer.v2.domain.StorageRef,
    original: String,
): String {
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

private suspend fun readTrashSummary(storage: StorageGateway, roots: List<TargetRoot>): TrashSummary {
    return roots.fold(TrashSummary()) { summary, root ->
        val child = runCatching {
            val trash = storage.findChild(root.directory, TRASH_DIRECTORY)
            if (trash?.kind == StorageEntryKind.DIRECTORY) summarizeDirectory(storage, trash.ref) else TrashSummary()
        }.getOrDefault(TrashSummary())
        TrashSummary(summary.fileCount + child.fileCount, summary.totalBytes + child.totalBytes)
    }
}

private suspend fun emptyTrash(storage: StorageGateway, roots: List<TargetRoot>): TrashSummary {
    return roots.fold(TrashSummary()) { summary, root ->
        val child = runCatching {
            val trash = storage.findChild(root.directory, TRASH_DIRECTORY)
            if (trash?.kind == StorageEntryKind.DIRECTORY) clearDirectoryContents(storage, trash.ref) else TrashSummary()
        }.getOrElse { throw it }
        TrashSummary(summary.fileCount + child.fileCount, summary.totalBytes + child.totalBytes)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KiB".format(bytes / 1_024.0)
    bytes < 1_024 * 1_024 * 1_024 -> "%.1f MiB".format(bytes / (1_024.0 * 1_024.0))
    else -> "%.1f GiB".format(bytes / (1_024.0 * 1_024.0 * 1_024.0))
}

private const val TRASH_DIRECTORY = "Drawer Trash"
