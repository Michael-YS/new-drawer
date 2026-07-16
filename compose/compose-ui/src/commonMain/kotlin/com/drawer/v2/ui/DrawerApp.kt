package com.drawer.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.PhotoCandidate
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.TargetRoot

/** Shared visual shell. Platform apps own directory pickers, storage and image decoding. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DrawerApp(
    status: String,
    sources: List<SourceRoot>,
    target: TargetRoot?,
    photo: PhotoCandidate?,
    categories: List<String>,
    newCategory: String,
    movesEnabled: Boolean,
    canUndo: Boolean,
    onAddSources: () -> Unit,
    onRemoveSource: (SourceRoot) -> Unit,
    onChooseTarget: () -> Unit,
    onSettings: () -> Unit,
    onUndo: () -> Unit,
    onCategory: (String) -> Unit,
    onNewCategoryChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSkip: () -> Unit,
    preview: @Composable (StorageRef, Modifier) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Drawer v2", style = MaterialTheme.typography.headlineMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium)
                DirectoryControls(sources, target, onAddSources, onRemoveSource, onChooseTarget, onSettings, canUndo, onUndo)
                PhotoPanel(photo, preview, Modifier.weight(1f).fillMaxWidth())
                CategoryPanel(categories, newCategory, onNewCategoryChanged, onCreateCategory, movesEnabled, onCategory, onSkip)
            }
        }
    }
}

@Composable
fun ConflictDialog(
    incoming: PhotoCandidate,
    existingFile: StorageRef,
    existingMetadata: MediaMetadata?,
    onSkip: () -> Unit,
    onRename: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable (StorageRef, Modifier) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A photo with this name already exists") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Incoming")
                    preview(incoming.file, Modifier.height(140.dp).fillMaxWidth())
                    Text(metadataSummary(incoming.metadata))
                }
                Column(Modifier.weight(1f)) {
                    Text("Existing")
                    preview(existingFile, Modifier.height(140.dp).fillMaxWidth())
                    Text(existingMetadata?.let(::metadataSummary) ?: "Metadata unavailable")
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
@OptIn(ExperimentalLayoutApi::class)
private fun DirectoryControls(
    sources: List<SourceRoot>, target: TargetRoot?, onAddSources: () -> Unit, onRemoveSource: (SourceRoot) -> Unit,
    onChooseTarget: () -> Unit, onSettings: () -> Unit, canUndo: Boolean, onUndo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onAddSources) { Text("Add source directories") }
            OutlinedButton(onClick = onChooseTarget) { Text("Choose target directory") }
            OutlinedButton(onClick = onSettings) { Text("Settings") }
            OutlinedButton(enabled = canUndo, onClick = onUndo) { Text("Undo last move") }
            Text("Target: ${target?.displayName ?: "not selected"}")
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { source ->
                FilterChip(selected = true, onClick = { onRemoveSource(source) }, label = { Text("${source.displayName} ×") })
            }
        }
    }
}

@Composable
private fun PhotoPanel(photo: PhotoCandidate?, preview: @Composable (StorageRef, Modifier) -> Unit, modifier: Modifier) {
    Surface(modifier = modifier, tonalElevation = 1.dp) {
        if (photo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending photo") }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview(photo.file, Modifier.weight(1f).fillMaxWidth())
                Text(metadataSummary(photo.metadata))
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    categories: List<String>, newCategory: String, onNewCategoryChanged: (String) -> Unit, onCreate: () -> Unit,
    movesEnabled: Boolean, onCategory: (String) -> Unit, onSkip: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category -> Button(enabled = movesEnabled, onClick = { onCategory(category) }) { Text(category) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newCategory, onNewCategoryChanged, label = { Text("New category") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreate) { Text("Create") }
            }
            OutlinedButton(enabled = movesEnabled, onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }
}

fun metadataSummary(metadata: MediaMetadata): String = buildString {
    append(metadata.name)
    append("\n${metadata.sizeBytes} bytes")
    if (metadata.width != null && metadata.height != null) append("\n${metadata.width} × ${metadata.height}")
    metadata.createdAtEpochMs?.let { append("\nCreated: $it") }
    append("\nModified: ${metadata.modifiedAtEpochMs}")
}
