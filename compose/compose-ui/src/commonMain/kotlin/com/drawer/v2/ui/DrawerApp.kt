package com.drawer.v2.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drawer.v2.domain.MediaMetadata
import com.drawer.v2.domain.PhotoCandidate
import com.drawer.v2.domain.SourceRoot
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.TargetRoot

private val DrawerColors = darkColorScheme(
    primary = Color(0xFFF2B84B),
    onPrimary = Color(0xFF231A08),
    background = Color(0xFF111315),
    onBackground = Color(0xFFF1F1EF),
    surface = Color(0xFF17191B),
    onSurface = Color(0xFFF1F1EF),
    surfaceVariant = Color(0xFF202326),
    onSurfaceVariant = Color(0xFFB9BCBF),
    outline = Color(0xFF44484C),
    error = Color(0xFFFFB4AB),
)

private val PanelShape = RoundedCornerShape(18.dp)

@Composable
fun DrawerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DrawerColors, content = content)
}

/** Shared visual shell. Platform apps own directory pickers, storage and image decoding. */
@Composable
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
    var directoriesExpanded by remember { mutableStateOf(sources.isEmpty() || target == null) }
    var categoryPanelExpanded by remember { mutableStateOf(false) }
    var categoryQuery by remember { mutableStateOf("") }
    var recentCategories by remember { mutableStateOf(categories.take(4)) }

    LaunchedEffect(categories) {
        val retained = recentCategories.filter(categories::contains)
        recentCategories = (retained + categories.filterNot(retained::contains)).take(4)
    }

    fun selectCategory(category: String) {
        recentCategories = prioritizeCategory(recentCategories, category)
        onCategory(category)
    }

    DrawerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 700.dp
                Column(Modifier.fillMaxSize()) {
                    AppHeader(
                        status = status,
                        sourceCount = sources.size,
                        target = target,
                        canUndo = canUndo,
                        directoriesExpanded = directoriesExpanded,
                        onToggleDirectories = { directoriesExpanded = !directoriesExpanded },
                        onUndo = onUndo,
                        onSettings = onSettings,
                        compact = compact,
                    )
                    if (directoriesExpanded || sources.isEmpty() || target == null) {
                        DirectoryPanel(
                            sources = sources,
                            target = target,
                            onAddSources = onAddSources,
                            onRemoveSource = onRemoveSource,
                            onChooseTarget = onChooseTarget,
                            compact = compact,
                        )
                    }
                    if (compact) {
                        MobileWorkspace(
                            photo = photo,
                            categories = categories,
                            recentCategories = recentCategories,
                            categoryQuery = categoryQuery,
                            newCategory = newCategory,
                            movesEnabled = movesEnabled,
                            expanded = categoryPanelExpanded,
                            onExpandedChange = { categoryPanelExpanded = it },
                            onQueryChange = { categoryQuery = it },
                            onCategory = ::selectCategory,
                            onNewCategoryChanged = onNewCategoryChanged,
                            onCreateCategory = onCreateCategory,
                            onSkip = onSkip,
                            preview = preview,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        DesktopWorkspace(
                            photo = photo,
                            categories = categories,
                            categoryQuery = categoryQuery,
                            newCategory = newCategory,
                            movesEnabled = movesEnabled,
                            onQueryChange = { categoryQuery = it },
                            onCategory = ::selectCategory,
                            onNewCategoryChanged = onNewCategoryChanged,
                            onCreateCategory = onCreateCategory,
                            onSkip = onSkip,
                            preview = preview,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    status: String,
    sourceCount: Int,
    target: TargetRoot?,
    canUndo: Boolean,
    directoriesExpanded: Boolean,
    onToggleDirectories: () -> Unit,
    onUndo: () -> Unit,
    onSettings: () -> Unit,
    compact: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().height(if (compact) 58.dp else 64.dp).padding(horizontal = if (compact) 14.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Drawer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (!compact) {
                StatusPill(status, Modifier.weight(1f).widthIn(max = 440.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = "$sourceCount source${if (sourceCount == 1) "" else "s"}",
                color = if (sourceCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleDirectories)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            if (!compact) {
                Text(
                    target?.displayName ?: "No target",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
            if (!compact) HeaderAction(if (directoriesExpanded) "Hide setup" else "Setup", onToggleDirectories)
            HeaderAction("Undo", onUndo, canUndo)
            HeaderAction("Settings", onSettings)
        }
    }
}

@Composable
private fun HeaderAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text = label,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(enabled = enabled, onClick = onClick).padding(8.dp),
    )
}

@Composable
private fun StatusPill(status: String, modifier: Modifier = Modifier) {
    Text(
        text = status,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DirectoryPanel(
    sources: List<SourceRoot>,
    target: TargetRoot?,
    onAddSources: () -> Unit,
    onRemoveSource: (SourceRoot) -> Unit,
    onChooseTarget: () -> Unit,
    compact: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 14.dp else 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddSources) { Text("Add source") }
                OutlinedButton(onClick = onChooseTarget) { Text(target?.let { "Target: ${it.displayName}" } ?: "Choose target") }
                sources.forEach { source ->
                    OutlinedButton(onClick = { onRemoveSource(source) }) { Text("${source.displayName}  ×") }
                }
            }
        }
    }
}

@Composable
private fun DesktopWorkspace(
    photo: PhotoCandidate?,
    categories: List<String>,
    categoryQuery: String,
    newCategory: String,
    movesEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onNewCategoryChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSkip: () -> Unit,
    preview: @Composable (StorageRef, Modifier) -> Unit,
    modifier: Modifier,
) {
    Row(modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PhotoPanel(photo, preview, Modifier.weight(1f).fillMaxHeight())
        CategorySidebar(
            categories = categories,
            query = categoryQuery,
            newCategory = newCategory,
            movesEnabled = movesEnabled,
            onQueryChange = onQueryChange,
            onCategory = onCategory,
            onNewCategoryChanged = onNewCategoryChanged,
            onCreateCategory = onCreateCategory,
            onSkip = onSkip,
            modifier = Modifier.width(350.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun CategorySidebar(
    categories: List<String>,
    query: String,
    newCategory: String,
    movesEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onNewCategoryChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = PanelShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose a category", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            CategorySearch(query, onQueryChange)
            val filtered = filteredCategories(categories, query)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it }) { category ->
                    CategoryButton(category, movesEnabled, onCategory, Modifier.fillMaxWidth())
                }
            }
            NewCategoryRow(newCategory, onNewCategoryChanged, onCreateCategory)
            OutlinedButton(enabled = movesEnabled, onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun MobileWorkspace(
    photo: PhotoCandidate?,
    categories: List<String>,
    recentCategories: List<String>,
    categoryQuery: String,
    newCategory: String,
    movesEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onNewCategoryChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSkip: () -> Unit,
    preview: @Composable (StorageRef, Modifier) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        PhotoPanel(photo, preview, Modifier.fillMaxSize())
        val collapsedHeight = if (recentCategories.isEmpty()) 116.dp else 212.dp
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight(0.72f) else Modifier.height(collapsedHeight))
                .animateContentSize()
                .pointerInput(expanded) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -6f) onExpandedChange(true)
                        if (dragAmount > 6f) onExpandedChange(false)
                    }
                },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            if (expanded) {
                ExpandedCategorySheet(
                    categories = categories,
                    query = categoryQuery,
                    newCategory = newCategory,
                    movesEnabled = movesEnabled,
                    onCollapse = { onExpandedChange(false) },
                    onQueryChange = onQueryChange,
                    onCategory = onCategory,
                    onNewCategoryChanged = onNewCategoryChanged,
                    onCreateCategory = onCreateCategory,
                    onSkip = onSkip,
                )
            } else {
                CollapsedCategorySheet(
                    categories = recentCategories,
                    movesEnabled = movesEnabled,
                    onExpand = { onExpandedChange(true) },
                    onCategory = onCategory,
                )
            }
        }
    }
}

@Composable
private fun SheetHandle(onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 44.dp, height = 4.dp)
            .clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline).clickable(onClick = onClick),
    )
}

@Composable
private fun CollapsedCategorySheet(
    categories: List<String>,
    movesEnabled: Boolean,
    onExpand: () -> Unit,
    onCategory: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SheetHandle(onExpand)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "All categories  ↑",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onExpand).padding(8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        CategoryGrid(categories, movesEnabled, onCategory, Modifier.weight(1f))
    }
}

@Composable
private fun ExpandedCategorySheet(
    categories: List<String>,
    query: String,
    newCategory: String,
    movesEnabled: Boolean,
    onCollapse: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onNewCategoryChanged: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SheetHandle(onCollapse)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Choose a category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Collapse  ↓", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onCollapse).padding(8.dp))
        }
        CategorySearch(query, onQueryChange)
        Spacer(Modifier.height(10.dp))
        CategoryGrid(filteredCategories(categories, query), movesEnabled, onCategory, Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        NewCategoryRow(newCategory, onNewCategoryChanged, onCreateCategory)
        OutlinedButton(enabled = movesEnabled, onClick = onSkip, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<String>,
    movesEnabled: Boolean,
    onCategory: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories.chunked(2), key = { it.joinToString("\u0000") }) { rowCategories ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    CategoryButton(category, movesEnabled, onCategory, Modifier.weight(1f))
                }
                if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryButton(category: String, enabled: Boolean, onCategory: (String) -> Unit, modifier: Modifier) {
    Button(
        enabled = enabled,
        onClick = { onCategory(category) },
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategorySearch(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search categories") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
    )
}

@Composable
private fun NewCategoryRow(newCategory: String, onChanged: (String) -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newCategory,
            onValueChange = onChanged,
            label = { Text("New category") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(11.dp),
        )
        Button(onClick = onCreate, enabled = newCategory.isNotBlank(), modifier = Modifier.height(56.dp)) { Text("Create") }
    }
}

@Composable
private fun PhotoPanel(photo: PhotoCandidate?, preview: @Composable (StorageRef, Modifier) -> Unit, modifier: Modifier) {
    Surface(modifier = modifier, shape = PanelShape, color = Color(0xFF0B0C0D)) {
        if (photo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No pending photos", style = MaterialTheme.typography.titleLarge)
                    Text("Add a source or wait for scanning to finish.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                preview(photo.file, Modifier.weight(1f).fillMaxWidth().background(Color.Black))
                Text(
                    metadataSummaryInline(photo.metadata),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
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
    DrawerTheme {
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
}

internal fun prioritizeCategory(recent: List<String>, category: String): List<String> =
    (listOf(category) + recent.filterNot { it == category }).take(4)

private fun filteredCategories(categories: List<String>, query: String): List<String> =
    if (query.isBlank()) categories else categories.filter { it.contains(query.trim(), ignoreCase = true) }

private fun metadataSummaryInline(metadata: MediaMetadata): String = buildString {
    append(metadata.name)
    append("   ·   ${formatFileSize(metadata.sizeBytes)}")
    if (metadata.width != null && metadata.height != null) append("   ·   ${metadata.width} × ${metadata.height}")
}

fun metadataSummary(metadata: MediaMetadata): String = buildString {
    append(metadata.name)
    append("\n${formatFileSize(metadata.sizeBytes)}")
    if (metadata.width != null && metadata.height != null) append("\n${metadata.width} × ${metadata.height}")
    metadata.createdAtEpochMs?.let { append("\nCreated: $it") }
    append("\nModified: ${metadata.modifiedAtEpochMs}")
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "${oneDecimal(bytes.toDouble() / (1024L * 1024L * 1024L))} GB"
    bytes >= 1024L * 1024L -> "${oneDecimal(bytes.toDouble() / (1024L * 1024L))} MB"
    bytes >= 1024L -> "${oneDecimal(bytes.toDouble() / 1024L)} KB"
    else -> "$bytes B"
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    return "${rounded / 10}.${rounded % 10}"
}
