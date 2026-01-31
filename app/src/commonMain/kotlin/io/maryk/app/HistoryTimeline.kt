package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.Check
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.ReferenceNullPair

@Composable
fun HistoryTimeline(
    versions: List<VersionedChanges>,
) {
    val clipboard = LocalClipboardManager.current
    var sortDescending by remember { mutableStateOf(true) }
    val chronological = versions.sortedBy { it.version }
    val sorted = if (sortDescending) chronological.asReversed() else chronological
    val latestBatchSize = 4
    val initialBatchSize = 3
    val showSplit = chronological.size > latestBatchSize + initialBatchSize
    val latestBatch = if (showSplit) chronological.takeLast(latestBatchSize) else chronological
    val initialBatch = if (showSplit) chronological.take(initialBatchSize) else emptyList()
    val latestBatchDisplay = if (sortDescending) latestBatch.asReversed() else latestBatch
    val initialBatchDisplay = if (sortDescending) initialBatch.asReversed() else initialBatch
    val previousByVersion = buildMap {
        sorted.forEachIndexed { index, entry ->
            put(entry.version, sorted.getOrNull(index + 1)?.version)
        }
    }
    var diffState by remember { mutableStateOf<DiffState?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("History", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { sortDescending = !sortDescending }, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = if (sortDescending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (sortDescending) "Newest first" else "Oldest first",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (sorted.isEmpty()) {
            Text("No history entries yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val onDiffAction: (ULong) -> Unit = onDiffAction@{ rightVersion ->
                val all = chronological.map { it.version }
                val rightIndex = all.indexOf(rightVersion)
                if (rightIndex < 0) return@onDiffAction
                val leftIndex = rightIndex - 1
                diffState = DiffState(all, leftIndex, rightIndex, versions)
            }
            if (showSplit && !sortDescending) {
                HistoryBatch(
                    title = null,
                    items = initialBatchDisplay,
                    previousByVersion = previousByVersion,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onDiff = onDiffAction,
                )
                HistoryBatch(
                    title = null,
                    items = latestBatchDisplay,
                    previousByVersion = previousByVersion,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onDiff = onDiffAction,
                )
            } else {
                HistoryBatch(
                    title = null,
                    items = latestBatchDisplay,
                    previousByVersion = previousByVersion,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onDiff = onDiffAction,
                )
                if (showSplit) {
                    HistoryBatch(
                        title = null,
                        items = initialBatchDisplay,
                        previousByVersion = previousByVersion,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                        onDiff = onDiffAction,
                    )
                }
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
        val entries = remember(item.changes) { buildChangeEntries(item.changes) }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatHlcTimestamp(item.version),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("v${item.version}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
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
                    IconButton(
                        onClick = { onDiff(item.version) },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Diff",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                ChangeEntryList(entries = entries, maxItems = 4)
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
    val rightIndex = state.rightIndex.coerceIn(0, state.versionsAsc.lastIndex)
    val leftIndex = state.leftIndex.coerceAtMost(rightIndex - 1).coerceAtLeast(-1)
    val leftVersion = if (leftIndex >= 0) state.versionsAsc.getOrNull(leftIndex) else null
    val rightVersion = state.versionsAsc.getOrNull(rightIndex)
    val rangeVersions = remember(state.versionsAsc, leftIndex, rightIndex) {
        val startIndex = if (leftIndex < 0) 0 else leftIndex + 1
        if (startIndex > rightIndex) emptyList() else state.versionsAsc.subList(startIndex, rightIndex + 1)
    }
    val mergedChanges = remember(state.allChanges, rangeVersions) {
        rangeVersions.flatMap { version ->
            state.allChanges.firstOrNull { it.version == version }?.changes.orEmpty()
        }
    }
    val entries = remember(mergedChanges) {
        val allEntries = buildChangeEntries(mergedChanges)
        allEntries
            .asReversed()
            .distinctBy { it.path }
            .asReversed()
    }
    val scrollState = rememberScrollState()

    ModalSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Diff", style = MaterialTheme.typography.titleMedium)
                    Text("Changes for selected version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close diff", modifier = Modifier.size(16.dp))
                }
            }
            val allowedLeft = remember(state.versionsAsc, rightVersion) {
                if (rightVersion == null) emptyList() else listOf<ULong?>(null) + state.versionsAsc.filter { it < rightVersion }
            }
            val allowedRight = remember(state.versionsAsc, leftVersion) {
                if (leftVersion == null) state.versionsAsc else state.versionsAsc.filter { it > leftVersion }
            }
            VersionCompareRow(
                leftVersions = allowedLeft,
                rightVersions = allowedRight.map { it as ULong? },
                leftSelected = leftVersion,
                rightSelected = rightVersion,
                onSelectLeft = { version ->
                    val newLeft = if (version == null) -1 else state.versionsAsc.indexOf(version).coerceAtLeast(0)
                    val newRight = state.rightIndex.coerceAtLeast(newLeft + 1)
                    onUpdate(state.copy(leftIndex = newLeft, rightIndex = newRight))
                },
                onSelectRight = { version ->
                    if (version == null) return@VersionCompareRow
                    val newRight = state.versionsAsc.indexOf(version).coerceAtLeast(0)
                    val newLeft = state.leftIndex.coerceAtMost(newRight - 1).coerceAtLeast(-1)
                    onUpdate(state.copy(leftIndex = newLeft, rightIndex = newRight))
                },
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entries.isEmpty()) {
                        Text("No changes in this version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ChangeEntryList(entries = entries, maxItems = Int.MAX_VALUE)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCompareRow(
    leftVersions: List<ULong?>,
    rightVersions: List<ULong?>,
    leftSelected: ULong?,
    rightSelected: ULong?,
    onSelectLeft: (ULong?) -> Unit,
    onSelectRight: (ULong?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VersionPicker(
            label = "From",
            versions = leftVersions,
            selected = leftSelected,
            onSelect = onSelectLeft,
            modifier = Modifier.weight(1f),
        )
        VersionPicker(
            label = "To",
            versions = rightVersions,
            selected = rightSelected,
            onSelect = onSelectRight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VersionPicker(
    label: String,
    versions: List<ULong?>,
    selected: ULong?,
    onSelect: (ULong?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clickable(enabled = versions.isNotEmpty()) { expanded = true },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    selected?.let { formatHlcTimestamp(it) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        ) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version?.let { formatHlcTimestamp(it) } ?: "—", style = MaterialTheme.typography.labelSmall) },
                    onClick = {
                        onSelect(version)
                        expanded = false
                    },
                )
            }
        }
    }
}

private enum class ChangeAction {
    ADDED,
    REMOVED,
    CHANGED,
}

private data class ChangeEntry(
    val action: ChangeAction,
    val path: String,
    val detail: String,
)

@Composable
private fun ActionCounts(entries: List<ChangeEntry>) {
    val added = entries.count { it.action == ChangeAction.ADDED }
    val removed = entries.count { it.action == ChangeAction.REMOVED }
    val changed = entries.count { it.action == ChangeAction.CHANGED }
    if (entries.isEmpty()) {
        Text("No changes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (added > 0) {
            ActionBadge(ChangeAction.ADDED, "$added")
        }
        if (removed > 0) {
            ActionBadge(ChangeAction.REMOVED, "$removed")
        }
        if (changed > 0) {
            ActionBadge(ChangeAction.CHANGED, "$changed")
        }
    }
}

@Composable
private fun ChangeEntryList(entries: List<ChangeEntry>, maxItems: Int) {
    val visibleEntries = entries.take(maxItems)
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleEntries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionBadge(entry.action)
                    Text(
                        entry.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.detail.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            val remaining = entries.size - visibleEntries.size
            if (remaining > 0) {
                Text("+$remaining more changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionBadge(action: ChangeAction, labelOverride: String? = null) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg, label) = when (action) {
        ChangeAction.ADDED -> Triple(colors.secondary.copy(alpha = 0.18f), colors.secondary, labelOverride ?: "A")
        ChangeAction.REMOVED -> Triple(colors.error.copy(alpha = 0.18f), colors.error, labelOverride ?: "R")
        ChangeAction.CHANGED -> Triple(colors.primary.copy(alpha = 0.18f), colors.primary, labelOverride ?: "C")
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun buildChangeEntries(changes: List<IsChange>): List<ChangeEntry> {
    if (changes.isEmpty()) return emptyList()
    val entries = mutableListOf<ChangeEntry>()
    for (change in changes) {
        when (change) {
            is ObjectCreate -> {
                entries.add(ChangeEntry(ChangeAction.ADDED, "Record", "created"))
            }
            is ObjectSoftDeleteChange -> {
                val action = if (change.isDeleted) ChangeAction.REMOVED else ChangeAction.ADDED
                val detail = if (change.isDeleted) "deleted" else "restored"
                entries.add(ChangeEntry(action, "Record", detail))
            }
            is Change -> {
                change.referenceValuePairs.forEach { pair ->
                    val action = if (pair is ReferenceNullPair<*>) ChangeAction.REMOVED else ChangeAction.CHANGED
                    val detail = if (pair is ReferenceNullPair<*>) "removed" else formatChangeValue(pair.value)
                    entries.add(ChangeEntry(action, pair.reference.toString(), detail))
                }
            }
            is Check -> {
                change.referenceValuePairs.forEach { pair ->
                    entries.add(ChangeEntry(ChangeAction.CHANGED, pair.reference.toString(), "checked ${formatChangeValue(pair.value)}"))
                }
            }
            is ListChange -> {
                change.listValueChanges.forEach { listChange ->
                    val path = listChange.reference.toString()
                    listChange.deleteValues?.let { deleted ->
                        entries.add(ChangeEntry(ChangeAction.REMOVED, path, "removed ${formatChangeValue(deleted)}"))
                    }
                    listChange.addValuesAtIndex?.forEach { (index, value) ->
                        entries.add(ChangeEntry(ChangeAction.ADDED, path, "added at $index: ${formatChangeValue(value)}"))
                    }
                    listChange.addValuesToEnd?.let { added ->
                        entries.add(ChangeEntry(ChangeAction.ADDED, path, "added ${formatChangeValue(added)}"))
                    }
                    if (listChange.deleteValues == null && listChange.addValuesAtIndex == null && listChange.addValuesToEnd == null) {
                        entries.add(ChangeEntry(ChangeAction.CHANGED, path, "list updated"))
                    }
                }
            }
            is SetChange -> {
                change.setValueChanges.forEach { setChange ->
                    val path = setChange.reference.toString()
                    val detail = setChange.addValues?.let { "added ${formatChangeValue(it)}" } ?: "set updated"
                    val action = if (setChange.addValues == null) ChangeAction.CHANGED else ChangeAction.ADDED
                    entries.add(ChangeEntry(action, path, detail))
                }
            }
            is IncMapChange -> {
                change.valueChanges.forEach { incChange ->
                    val path = incChange.reference.toString()
                    val detail = incChange.addValues?.let { "added ${formatChangeValue(it)}" } ?: "map updated"
                    val action = if (incChange.addValues == null) ChangeAction.CHANGED else ChangeAction.ADDED
                    entries.add(ChangeEntry(action, path, detail))
                }
            }
            is IncMapAddition -> {
                change.additions.forEach { addition ->
                    val path = addition.reference.toString()
                    val keys = addition.addedKeys.orEmpty()
                    val values = addition.addedValues.orEmpty()
                    val pairs = keys.zip(values).joinToString(prefix = "{", postfix = "}") { (key, value) ->
                        "${formatChangeValue(key)}: ${formatChangeValue(value)}"
                    }
                    val detail = if (pairs == "{}") "map updated" else "added $pairs"
                    val action = if (pairs == "{}") ChangeAction.CHANGED else ChangeAction.ADDED
                    entries.add(ChangeEntry(action, path, detail))
                }
            }
            is MultiTypeChange -> {
                change.referenceTypePairs.forEach { pair ->
                    entries.add(ChangeEntry(ChangeAction.CHANGED, pair.reference.toString(), "type ${pair.type}"))
                }
            }
            is IndexChange -> {
                entries.add(ChangeEntry(ChangeAction.CHANGED, "Index", "index updated"))
            }
            else -> {
                entries.add(ChangeEntry(ChangeAction.CHANGED, "Change", change.toString()))
            }
        }
    }
    return entries
}

private fun formatChangeValue(value: Any?): String = when (value) {
    null -> "—"
    is String -> value
    is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { formatChangeValue(it) }
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { "${formatChangeValue(it.key)}: ${formatChangeValue(it.value)}" }
    else -> value.toString()
}
