package io.maryk.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val headerHeight = 32.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ResultsDataGrid(
    state: BrowserState,
    uiState: BrowserUiState,
    modifier: Modifier = Modifier,
) {
    val rows = state.scanResults
    val listState = rememberLazyListState()
    var contextMenuRow by remember { mutableStateOf<ScanRow?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var anchorIndex by remember { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    var deleteRow by remember { mutableStateOf<ScanRow?>(null) }
    var hardDelete by remember { mutableStateOf(false) }
    val densityHeight = when (uiState.gridDensity) {
        GridDensity.COMPACT -> 40.dp
        GridDensity.STANDARD -> 58.dp
        GridDensity.COMFY -> 70.dp
    }

    LaunchedEffect(rows) {
        uiState.selectedRowKeys.clear()
    }

    val pendingKey = state.pendingHighlightKey
    val pendingModelId = state.pendingHighlightModelId
    LaunchedEffect(rows, pendingKey, pendingModelId, state.selectedModelId) {
        if (pendingKey != null && pendingModelId != null && pendingModelId == state.selectedModelId) {
            val index = rows.indexOfFirst { it.key == pendingKey }
            if (index >= 0) {
                uiState.selectedRowKeys.clear()
                uiState.selectedRowKeys[pendingKey] = true
                anchorIndex = index
                state.openRecord(rows[index])
                state.clearPendingHighlight()
                listState.scrollToItem(index)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val maxKeyWidth = (maxWidth - 160.dp).coerceAtLeast(140.dp)
        var keyColumnWidth by remember { mutableStateOf(200.dp) }
        val clampedKeyWidth = keyColumnWidth.coerceIn(140.dp, maxKeyWidth)

        Column(modifier = Modifier.fillMaxHeight()) {
            state.referenceBackTarget?.let { target ->
                Surface(
                    color = Color(0xFFFFF3BF),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.backToReferenceTarget() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                        Text(
                            "Back to ${target.modelName} ${target.keyText}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { state.clearReferenceBackTarget() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close back bar", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(ResultsTab.DATA, ResultsTab.MODEL)
                TabRow(
                    selectedTabIndex = tabs.indexOf(uiState.resultsTab).coerceAtLeast(0),
                    modifier = Modifier.height(28.dp)
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = uiState.resultsTab == tab,
                            onClick = { uiState.updateResultsTab(tab) },
                            modifier = Modifier.height(27.dp),
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.resultsTab == ResultsTab.DATA) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val selectedIndex = rows.indexOfFirst { uiState.selectedRowKeys[it.key] == true }
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        val nextIndex = (selectedIndex + 1).coerceAtMost(rows.lastIndex)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        true
                                    }
                                    Key.PageDown -> {
                                        val nextIndex = (selectedIndex + 10).coerceAtMost(rows.lastIndex)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        true
                                    }
                                    Key.PageUp -> {
                                        val nextIndex = (selectedIndex - 10).coerceAtLeast(0)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        true
                                    }
                                    Key.Enter -> {
                                        val selected = rows.getOrNull(selectedIndex)
                                        if (selected != null) {
                                            state.openRecord(selected)
                                        }
                                        true
                                    }
                                    Key.Escape -> {
                                        uiState.selectedRowKeys.clear()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    ) {
                        stickyHeader {
                            GridHeaderRow(
                                keyWidth = clampedKeyWidth,
                                onResize = { delta ->
                                    keyColumnWidth = (keyColumnWidth + delta).coerceIn(140.dp, maxKeyWidth)
                                },
                            )
                        }
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            val selected = uiState.selectedRowKeys[row.key] == true
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPointerEvent(PointerEventType.Press) { event ->
                                        if (event.buttons.isSecondaryPressed) {
                                            val position = event.changes.first().position
                                            contextMenuOffset = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
                                            contextMenuRow = row
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = densityHeight)
                                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .clickable {
                                            updateSelection(rows, index, uiState, null, anchorIndex) { anchorIndex = it }
                                            state.openRecord(row)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    Text(
                                        row.keyText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(clampedKeyWidth),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        row.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (contextMenuRow == row) {
                                    ResultRowContextMenu(
                                        offset = contextMenuOffset,
                                        onDismiss = { contextMenuRow = null },
                                        onCopyRowJson = {
                                            val model = state.currentDataModel() ?: return@ResultRowContextMenu
                                            val json = serializeValuesToJson(model, row.values, buildRequestContext(model))
                                            clipboard.setText(AnnotatedString(json))
                                        },
                                        onCopyRowYaml = {
                                            val model = state.currentDataModel() ?: return@ResultRowContextMenu
                                            val yaml = serializeValuesToYaml(model, row.values, buildRequestContext(model))
                                            clipboard.setText(AnnotatedString(yaml))
                                        },
                                        onCopyKey = { clipboard.setText(AnnotatedString(row.keyText)) },
                                        onDelete = {
                                            deleteRow = row
                                            hardDelete = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                } else {
                    ModelTabPanel(state, modifier = Modifier.fillMaxHeight().fillMaxWidth())
                }
            }
        }
    }
    if (deleteRow != null) {
        val model = state.currentDataModel()
        val yaml = if (model != null && deleteRow != null) {
            serializeValuesToYaml(model, deleteRow!!.values, buildRequestContext(model))
        } else {
            ""
        }
        ModalSurface(onDismiss = { deleteRow = null }) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Delete record", style = MaterialTheme.typography.titleSmall)
                Text(deleteRow!!.keyText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val scrollState = rememberScrollState()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                        ) {
                            Text(yaml, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = hardDelete, onCheckedChange = { hardDelete = it })
                    Text("Hard delete", style = MaterialTheme.typography.bodySmall)
                }
                if (hardDelete) {
                    Text(
                        "Hard delete is permanent and cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { deleteRow = null },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            state.deleteRow(deleteRow!!, hardDelete)
                            deleteRow = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridHeaderRow(
    keyWidth: Dp,
    onResize: (Dp) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(headerHeight).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                "Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(keyWidth),
            )
            ColumnResizeHandle(onResize = onResize)
            HeaderCell("Values", 1f)
        }
    }
}

@Composable
private fun RowScope.HeaderCell(label: String, weight: Float) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(weight))
}

@Composable
private fun ColumnResizeHandle(
    onResize: (Dp) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(6.dp)
            .fillMaxHeight()
            .horizontalResizeCursor()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onResize(with(density) { dragAmount.x.toDp() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(outline),
        )
    }
}

@Composable
fun ResultRowContextMenu(
    offset: DpOffset,
    onDismiss: () -> Unit,
    onCopyRowJson: () -> Unit,
    onCopyRowYaml: () -> Unit,
    onCopyKey: () -> Unit,
    onDelete: () -> Unit,
) {
    val itemPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    val itemStyle = MaterialTheme.typography.labelSmall
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, offset = offset) {
        DropdownMenuItem(
            text = { Text("Copy key", style = itemStyle) },
            contentPadding = itemPadding,
            onClick = {
                onCopyKey()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy data as JSON", style = itemStyle) },
            contentPadding = itemPadding,
            onClick = {
                onCopyRowJson()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy data as YAML", style = itemStyle) },
            contentPadding = itemPadding,
            onClick = {
                onCopyRowYaml()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error, style = itemStyle) },
            contentPadding = itemPadding,
            onClick = {
                onDelete()
                onDismiss()
            },
        )
    }
}

private fun updateSelection(
    rows: List<ScanRow>,
    index: Int,
    uiState: BrowserUiState,
    event: KeyEvent?,
    anchorIndex: Int?,
    onAnchorChange: (Int) -> Unit,
) {
    val row = rows.getOrNull(index) ?: return
    val toggle = event?.isCtrlPressed == true || event?.isMetaPressed == true
    if (event?.isShiftPressed == true && anchorIndex != null) {
        uiState.selectedRowKeys.clear()
        val anchor = anchorIndex
        val start = minOf(anchor, index)
        val end = maxOf(anchor, index)
        rows.subList(start, end + 1).forEach { uiState.selectedRowKeys[it.key] = true }
        return
    }
    if (!toggle) {
        uiState.selectedRowKeys.clear()
    }
    val current = uiState.selectedRowKeys[row.key] == true
    uiState.selectedRowKeys[row.key] = !current
    onAnchorChange(index)
}
