package io.maryk.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.VersionedChanges

@Composable
fun HistoryTimeline(
    versions: List<VersionedChanges>,
) {
    val clipboard = LocalClipboardManager.current
    val sorted = versions.sortedByDescending { it.version }
    val latestBatchSize = 4
    val initialBatchSize = 3
    val showSplit = sorted.size > latestBatchSize + initialBatchSize
    val latestBatch = if (showSplit) sorted.take(latestBatchSize) else sorted
    val initialBatch = if (showSplit) sorted.takeLast(initialBatchSize) else emptyList()
    val previousByVersion = buildMap {
        sorted.forEachIndexed { index, entry ->
            put(entry.version, sorted.getOrNull(index + 1)?.version)
        }
    }
    var diffState by remember { mutableStateOf<DiffState?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("History", style = MaterialTheme.typography.labelMedium)
        if (latestBatch.isEmpty()) {
            Text("No history entries yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HistoryBatch(
                title = if (showSplit) "Latest" else null,
                items = latestBatch,
                previousByVersion = previousByVersion,
                onCopy = { clipboard.setText(AnnotatedString(it)) },
                onDiff = { rightVersion ->
                    val all = sorted.map { it.version }.sorted()
                    val rightIndex = all.indexOf(rightVersion)
                    val leftIndex = (rightIndex - 1).coerceAtLeast(0)
                    if (rightIndex <= 0) return@HistoryBatch
                    diffState = DiffState(all, leftIndex, rightIndex, versions)
                },
            )
            if (showSplit) {
                Text("Initial", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryBatch(
                    title = null,
                    items = initialBatch,
                    previousByVersion = previousByVersion,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onDiff = { rightVersion ->
                        val all = sorted.map { it.version }.sorted()
                        val rightIndex = all.indexOf(rightVersion)
                        val leftIndex = (rightIndex - 1).coerceAtLeast(0)
                        if (rightIndex <= 0) return@HistoryBatch
                        diffState = DiffState(all, leftIndex, rightIndex, versions)
                    },
                )
            }
        }
    }

    diffState?.let { state ->
        DiffDialog(
            state = state,
            onDismiss = { diffState = null },
            onUpdate = { diffState = it },
        )
    }
}

@Composable
private fun HistoryBatch(
    title: String?,
    items: List<VersionedChanges>,
    previousByVersion: Map<ULong, ULong?>,
    onCopy: (String) -> Unit,
    onDiff: (ULong) -> Unit,
) {
    if (title != null) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items.forEach { item ->
        val previousVersion = previousByVersion[item.version]
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatHlcTimestamp(item.version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("v${item.version}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        IconButton(
                            onClick = { onCopy(item.version.toString()) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy version",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { onDiff(item.version) },
                    enabled = previousVersion != null,
                ) {
                    Text("Diff", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private data class DiffState(
    val versionsAsc: List<ULong>,
    val leftIndex: Int,
    val rightIndex: Int,
    val allChanges: List<VersionedChanges>,
)

@Composable
private fun DiffDialog(
    state: DiffState,
    onDismiss: () -> Unit,
    onUpdate: (DiffState) -> Unit,
) {
    val leftIndex = state.leftIndex.coerceAtMost(state.rightIndex - 1)
    val leftVersion = state.versionsAsc.getOrNull(leftIndex)
    val rightVersion = state.versionsAsc.getOrNull(state.rightIndex)
    val rightChanges = state.allChanges.firstOrNull { it.version == rightVersion }?.changes.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diff") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionPickerRow(
                    label = "Compare",
                    versions = state.versionsAsc.take(state.rightIndex),
                    selected = leftVersion,
                    onSelect = { version ->
                        val newLeft = state.versionsAsc.indexOf(version)
                        onUpdate(state.copy(leftIndex = newLeft))
                    },
                )
                VersionPickerRow(
                    label = "Selected",
                    versions = state.versionsAsc,
                    selected = rightVersion,
                    onSelect = { version ->
                        val newRight = state.versionsAsc.indexOf(version)
                        val newLeft = (newRight - 1).coerceAtLeast(0)
                        onUpdate(state.copy(leftIndex = newLeft, rightIndex = newRight))
                    },
                )
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        changesToYaml(rightChanges),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun VersionPickerRow(
    label: String,
    versions: List<ULong>,
    selected: ULong?,
    onSelect: (ULong) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        ) {
            Text(
                selected?.let { "v$it" } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        TextButton(onClick = { expanded = true }, enabled = versions.isNotEmpty()) {
            Text("Change", style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text("v$version", style = MaterialTheme.typography.labelSmall) },
                    onClick = {
                        onSelect(version)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun changesToYaml(changes: List<IsChange>): String {
    if (changes.isEmpty()) return "no changes"
    return changes.joinToString(separator = "\n") { change ->
        "- ${change}"
    }
}
