package io.maryk.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.values.AbstractValues
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.requests.get

@Composable
fun InspectorDrawer(
    state: BrowserState,
    uiState: BrowserUiState,
    modifier: Modifier = Modifier,
) {
    val details = state.recordDetails
    var tab by remember { mutableStateOf(InspectorTab.DATA) }
    val inModelMode = uiState.resultsTab == ResultsTab.MODEL
    val tabs = remember(inModelMode) {
        if (inModelMode) listOf(InspectorTab.DATA, InspectorTab.RAW) else InspectorTab.entries.toList()
    }
    LaunchedEffect(inModelMode) {
        if (tab !in tabs) {
            tab = InspectorTab.DATA
        }
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (inModelMode) {
                ModelDetailsHeader(state)
            } else {
                if (details == null) {
                    EmptyStateCard("No record selected", "Select a row to inspect details.")
                    return
                }
                InspectorHeader(details)
            }
            TabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                tabs.forEach { item ->
                    Tab(
                        selected = tab == item,
                        onClick = { tab = item },
                        modifier = Modifier.height(28.dp),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        },
                    )
                }
            }
            when (tab) {
                InspectorTab.DATA -> {
                    if (inModelMode) {
                        ModelDetailsPanel(state, modifier = Modifier.fillMaxHeight())
                    } else if (details != null) {
                        InspectorData(state, details)
                    }
                }
                InspectorTab.RAW -> {
                    if (inModelMode) {
                        ModelRawPanel(state, modifier = Modifier.fillMaxHeight())
                    } else if (details != null) {
                        InspectorRaw(state, details)
                    }
                }
                InspectorTab.HISTORY -> {
                    if (!inModelMode) {
                        HistoryTimeline(
                            versions = state.historyChanges,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDetailsHeader(
    state: BrowserState,
) {
    val dataModel = state.currentDataModel()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            dataModel?.Meta?.name ?: "No model",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InspectorHeader(details: RecordDetails) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                details.keyText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SmallCopyButton()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimestampRow("Created", details.firstVersion)
            TimestampRow("Updated", details.lastVersion)
            if (details.isDeleted) {
                InfoChip("Deleted", tone = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SmallCopyButton(
    onClick: () -> Unit = {},
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(18.dp),
    ) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimestampRow(
    label: String,
    version: ULong,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatHlcTimestamp(version),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun InspectorData(
    state: BrowserState,
    details: RecordDetails,
    showEdit: Boolean = true,
) {
    var fieldSearch by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val values = remember(details.yaml, details.model) {
        runCatching { parseValuesFromYaml(details.model, details.yaml) }.getOrNull()
    }
    val nodes = remember(values) {
        values?.let { buildFieldNodes(it) }.orEmpty()
    }
    val filtered = remember(nodes, fieldSearch) {
        if (fieldSearch.isBlank()) {
            nodes
        } else {
            filterFieldNodes(nodes, fieldSearch)
        }
    }
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Data", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (showEdit) {
                IconButton(
                    onClick = { showEditor = true },
                    modifier = Modifier.size(28.dp),
                    enabled = values != null,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit data",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            IconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) {
                        fieldSearch = ""
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search data fields",
                    tint = if (showSearch) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (showSearch) {
            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
            QuerySearchField(
                value = fieldSearch,
                onValueChange = { fieldSearch = it },
                placeholder = "Search fields",
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            )
        }
        if (filtered.isEmpty()) {
            Text("No matching fields.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            filtered.forEach { node ->
                FieldNodeView(state, node)
            }
        }
    }
    if (showEditor && values != null) {
        RecordEditorDialog(
            state = state,
            mode = RecordEditorMode.EDIT,
            dataModel = details.model,
            initialValues = values,
            initialKeyText = details.keyText,
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun InspectorRaw(state: BrowserState, details: RecordDetails) {
    var search by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var awaitingSaveResult by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val query = search.trim()
    val lines = remember(details.yaml) { details.yaml.lines() }
    val filteredLines = remember(query, lines) {
        if (query.isBlank()) {
            lines
        } else {
            lines.filter { it.contains(query, ignoreCase = true) }
        }
    }
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val baseStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val highlightedLines = remember(filteredLines, query, highlightColor) {
        if (query.isBlank()) {
            emptyList()
        } else {
            filteredLines.map { line ->
                buildHighlightedLine(line, query, highlightColor)
            }
        }
    }
    val lastMessage = state.lastActionMessage
    if (awaitingSaveResult && lastMessage != null) {
        if (lastMessage.contains("failed", ignoreCase = true)) {
            showError = true
            errorMessage = lastMessage
            awaitingSaveResult = false
        } else if (lastMessage.startsWith("Updated", ignoreCase = true)) {
            awaitingSaveResult = false
            isEditing = false
        }
    }
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Raw", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(details.yaml)) },
                modifier = Modifier.size(20.dp).alpha(0.65f),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy raw data",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            IconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) {
                        search = ""
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search raw data",
                    tint = if (showSearch) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            IconButton(onClick = { isEditing = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit raw data", modifier = Modifier.size(14.dp))
            }
        }
        if (showSearch) {
            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
            QuerySearchField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Search data",
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
            SelectionContainer {
                if (query.isBlank()) {
                    Text(
                        text = details.yaml,
                        style = baseStyle,
                    )
                } else {
                    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        highlightedLines.forEach { line ->
                            Surface(
                                color = highlightColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = line,
                                    style = baseStyle,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isEditing) {
        ModalSurface(onDismiss = { isEditing = false }) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Edit raw data", style = MaterialTheme.typography.titleMedium)
                        Text(details.keyText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { isEditing = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close editor")
                    }
                }
                val scrollState = rememberScrollState()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = details.editedYaml,
                                onValueChange = { state.updateRecordEditor(it) },
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Black),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            state.resetRecordEditor()
                            isEditing = false
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            awaitingSaveResult = true
                            state.applyRecordChanges()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Save failed") },
            text = { Text(errorMessage, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("Ok")
                }
            },
        )
    }
}

private fun buildHighlightedLine(
    line: String,
    query: String,
    highlightColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(line)
    return buildAnnotatedString {
        val lowerLine = line.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0
        while (true) {
            val index = lowerLine.indexOf(lowerQuery, startIndex)
            if (index == -1) {
                append(line.substring(startIndex))
                break
            }
            if (index > startIndex) {
                append(line.substring(startIndex, index))
            }
            withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold)) {
                append(line.substring(index, index + query.length))
            }
            startIndex = index + query.length
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataField(field: FieldEntry, indent: Int = 0, state: BrowserState) {
    val tooltip = field.type.ifBlank { "Unknown" }
    if (field.reference != null) {
        Row(
            modifier = Modifier.padding(start = (indent * 12).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
            ReferenceValue(state, field.reference)
        }
    } else {
        HoverTooltip(
            text = tooltip,
            monospace = false,
        ) { hoverModifier ->
            SelectionContainer {
                Row(
                    modifier = hoverModifier.padding(start = (indent * 12).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
                    Text(field.value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceValue(
    state: BrowserState,
    reference: ReferenceMeta,
) {
    var preview by remember(reference.keyText, reference.modelName, state.activeConnection) { mutableStateOf<String?>(null) }
    var loading by remember(reference.keyText, reference.modelName, state.activeConnection) { mutableStateOf(false) }
    LaunchedEffect(reference.keyText, reference.modelName, state.activeConnection) {
        val connection = state.activeConnection ?: return@LaunchedEffect
        val (_, dataModel) = state.resolveModelByName(reference.modelName) ?: return@LaunchedEffect
        loading = true
        val yaml = withContext(Dispatchers.IO) {
            runCatching {
                val response = connection.dataStore.execute(
                    dataModel.get(reference.key, filterSoftDeleted = false)
                )
                response.values.firstOrNull()?.let { serializeRecordToYaml(dataModel, it) }
            }.getOrNull()
        }
        loading = false
        preview = yaml?.let { buildPreviewYaml(it) } ?: "Not found."
    }
    val tooltipText = when {
        loading -> "Loading…"
        preview.isNullOrBlank() -> "Not found."
        else -> preview.orEmpty()
    }
    HoverTooltip(
        text = tooltipText,
        monospace = true,
    ) { hoverModifier ->
        Text(
            reference.keyText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                textDecoration = TextDecoration.Underline,
            ),
            modifier = hoverModifier.clickable {
                state.openReference(reference.modelName, reference.key)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoverTooltip(
    text: String,
    monospace: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    if (text.isBlank()) {
        content(Modifier)
        return
    }
    TooltipArea(
        tooltip = { TooltipCard(text, monospace) },
        delayMillis = 600,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            alignment = Alignment.BottomEnd,
            offset = DpOffset(12.dp, 8.dp),
        ),
    ) {
        content(Modifier)
    }
}

@Composable
private fun InfoChip(label: String, tone: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = tone.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tone, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

enum class InspectorTab(val label: String) {
    DATA("Data"),
    RAW("Raw"),
    HISTORY("History"),
}

private data class FieldEntry(
    val label: String,
    val value: String,
    val type: String,
    val reference: ReferenceMeta? = null,
)

private data class FieldNode(
    val label: String,
    val value: String,
    val type: String,
    val children: List<FieldNode> = emptyList(),
    val count: Int? = null,
    val defaultExpanded: Boolean = true,
    val reference: ReferenceMeta? = null,
)

private data class ReferenceMeta(
    val modelName: String,
    val key: Key<IsRootDataModel>,
    val keyText: String,
)

@Composable
private fun TooltipCard(
    text: String,
    monospace: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 6.dp,
    ) {
        Text(
            text,
            style = if (monospace) {
                MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.labelSmall
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildFieldNodes(values: AbstractValues<*, *>): List<FieldNode> {
    val dataModel = values.dataModel
    val nodes = mutableListOf<FieldNode>()
    for (item in values) {
        val wrapper = dataModel[item.index] as? IsDefinitionWrapper<*, *, *, *>
        val label = wrapper?.name ?: item.index.toString()
        val type = wrapper?.definition?.let { definitionTypeHint(it) }.orEmpty()
        val reference = buildReferenceMeta(wrapper, item.value)
        when (val value = item.value) {
            is AbstractValues<*, *> -> {
                FieldNode(
                    label = label,
                    value = "",
                    type = type,
                    children = buildFieldNodes(value),
                    defaultExpanded = true,
                )
            }
            is ValueDataObjectWithValues -> {
                FieldNode(
                    label = label,
                    value = "",
                    type = type.ifBlank { "ValueObject" },
                    children = buildFieldNodes(value.values),
                    defaultExpanded = true,
                )
            }
            is TypedValue<*, *> -> {
                buildNodeForTypedValue(
                    label = label,
                    typedValue = value,
                    fallbackType = type,
                )
            }
            is Map<*, *> -> {
                FieldNode(
                    label = label,
                    value = "",
                    type = type.ifBlank { "Map" },
                    children = value.entries.map { entry ->
                        buildNodeForValue(
                            label = formatFieldValue(entry.key),
                            value = entry.value,
                            fallbackType = "Entry",
                        )
                    },
                    count = value.size,
                    defaultExpanded = false,
                )
            }
            is Collection<*> -> {
                FieldNode(
                    label = label,
                    value = "",
                    type = type.ifBlank { "List" },
                    children = value.mapIndexed { index, itemValue ->
                        buildNodeForValue(
                            label = "Item ${index + 1}",
                            value = itemValue,
                            fallbackType = "Item",
                        )
                    },
                    count = value.size,
                    defaultExpanded = false,
                )
            }
            else -> {
                FieldNode(
                    label = label,
                    value = formatFieldValue(value),
                    type = type,
                    reference = reference,
                )
            }
        }.also(nodes::add)
    }
    return nodes
}

private fun buildNodeForValue(
    label: String,
    value: Any?,
    fallbackType: String,
): FieldNode = when (value) {
    is AbstractValues<*, *> -> FieldNode(
        label = label,
        value = "",
        type = fallbackType,
        children = buildFieldNodes(value),
        defaultExpanded = true,
    )
    is ValueDataObjectWithValues -> FieldNode(
        label = label,
        value = "",
        type = fallbackType,
        children = buildFieldNodes(value.values),
        defaultExpanded = true,
    )
    is TypedValue<*, *> -> buildNodeForTypedValue(label, value, fallbackType)
    is Map<*, *> -> FieldNode(
        label = label,
        value = "",
        type = fallbackType,
        children = value.entries.map { entry ->
            buildNodeForValue(
                label = formatFieldValue(entry.key),
                value = entry.value,
                fallbackType = "Entry",
            )
        },
        count = value.size,
        defaultExpanded = false,
    )
    is Collection<*> -> FieldNode(
        label = label,
        value = "",
        type = fallbackType,
        children = value.mapIndexed { index, itemValue ->
            buildNodeForValue(
                label = "Item ${index + 1}",
                value = itemValue,
                fallbackType = "Item",
            )
        },
        count = value.size,
        defaultExpanded = false,
    )
    else -> FieldNode(
        label = label,
        value = formatFieldValue(value),
        type = fallbackType,
    )
}

private fun filterFieldNodes(nodes: List<FieldNode>, query: String): List<FieldNode> {
    val lower = query.lowercase()
    return nodes.mapNotNull { node ->
        val childMatches = filterFieldNodes(node.children, query)
        val matchesSelf = node.label.lowercase().contains(lower) || node.value.lowercase().contains(lower)
        when {
            matchesSelf -> node
            childMatches.isNotEmpty() -> node.copy(children = childMatches, count = childMatches.size)
            else -> null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldNodeView(state: BrowserState, node: FieldNode, indent: Int = 0) {
    if (node.children.isEmpty()) {
        DataField(FieldEntry(node.label, node.value, node.type, node.reference), indent, state)
        return
    }
    var expanded by remember(node.label) { mutableStateOf(node.defaultExpanded) }
    val headerLabel = if (node.count != null) "${node.label} (${node.count})" else node.label
    HoverTooltip(
        text = node.type.ifBlank { "Embedded" },
        monospace = false,
    ) { hoverModifier ->
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(6.dp),
            modifier = hoverModifier
                .fillMaxWidth()
                .padding(start = (indent * 12).dp, top = 2.dp, bottom = 0.dp)
                .offset(x = (-6).dp)
                .clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(headerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    if (expanded) {
        node.children.forEach { child ->
            FieldNodeView(state, child, indent + 1)
        }
    }
}

private fun buildReferenceMeta(
    wrapper: IsDefinitionWrapper<*, *, *, *>?,
    value: Any?,
): ReferenceMeta? {
    val dataModel = when (val definition = wrapper?.definition) {
        is IsReferenceDefinition<*, *> -> definition.dataModel
        else -> null
    } ?: return null
    val resolvedKey = when (value) {
        is Key<*> -> value
        null -> null
        else -> runCatching { dataModel.key(value.toString()) }.getOrNull()
    } ?: return null
    @Suppress("UNCHECKED_CAST")
    val castKey = resolvedKey as Key<IsRootDataModel>
    val modelName = dataModel.Meta.name
    return ReferenceMeta(modelName = modelName, key = castKey, keyText = resolvedKey.toString())
}

private fun buildPreviewYaml(yaml: String, maxLines: Int = 10): String {
    val lines = yaml.lines()
    if (lines.size <= maxLines) return yaml
    return lines.take(maxLines).joinToString(separator = "\n") + "\n…"
}

private fun formatFieldValue(value: Any?): String = when (value) {
    null -> "—"
    is String -> value
    is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { formatFieldValue(it) }
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { "${formatFieldValue(it.key)}: ${formatFieldValue(it.value)}" }
    is AbstractValues<*, *> -> value.toString()
    is TypedValue<*, *> -> "${value.type.name}: ${formatFieldValue(value.value)}"
    else -> value.toString()
}

private fun definitionTypeHint(definition: IsPropertyDefinition<*>): String {
    return when (definition) {
        is NumberDefinition<*> -> "Number (${definition.type.type.name})"
        else -> definition::class.simpleName.orEmpty()
    }
}

private fun buildNodeForTypedValue(
    label: String,
    typedValue: TypedValue<*, *>,
    fallbackType: String,
): FieldNode {
    val typeLabel = typedValue.type.name
    val inner = typedValue.value
    return when (inner) {
        is AbstractValues<*, *> -> FieldNode(
            label = "$label ($typeLabel)",
            value = "",
            type = fallbackType,
            children = buildFieldNodes(inner),
            defaultExpanded = true,
        )
        is ValueDataObjectWithValues -> FieldNode(
            label = "$label ($typeLabel)",
            value = "",
            type = fallbackType,
            children = buildFieldNodes(inner.values),
            defaultExpanded = true,
        )
        is Map<*, *> -> FieldNode(
            label = "$label ($typeLabel)",
            value = "",
            type = fallbackType,
            children = inner.entries.map { entry ->
                buildNodeForValue(
                    label = formatFieldValue(entry.key),
                    value = entry.value,
                    fallbackType = "Entry",
                )
            },
            count = inner.size,
            defaultExpanded = false,
        )
        is Collection<*> -> FieldNode(
            label = "$label ($typeLabel)",
            value = "",
            type = fallbackType,
            children = inner.mapIndexed { index, itemValue ->
                buildNodeForValue(
                    label = "Item ${index + 1}",
                    value = itemValue,
                    fallbackType = "Item",
                )
            },
            count = inner.size,
            defaultExpanded = false,
        )
        else -> FieldNode(
            label = "$label ($typeLabel)",
            value = formatFieldValue(inner),
            type = fallbackType,
        )
    }
}
