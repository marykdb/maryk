package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values
import kotlin.math.roundToInt

private val headerHeight = 32.dp
private val indexColumnWidth = 160.dp
private val valuesColumnWidth = 260.dp
private val pinnedColumnWidth = 200.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultsDataGrid(
    state: BrowserState,
    uiState: BrowserUiState,
    modifier: Modifier = Modifier,
) {
    val rows = state.scanResults
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var anchorIndex by remember { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboardManager.current
    var deleteRow by remember { mutableStateOf<ScanRow?>(null) }
    var hardDelete by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editRow by remember { mutableStateOf<ScanRow?>(null) }
    val dataModel = state.currentDataModel()
    val indexColumns = remember(dataModel) { buildIndexColumns(dataModel) }
    val pinnedPaths = uiState.pinnedPaths(state.selectedModelId)
    val pinnedColumns = remember(dataModel, pinnedPaths) {
        if (dataModel == null || pinnedPaths.isEmpty()) {
            emptyList()
        } else {
            val context = buildRequestContext(dataModel)
            pinnedPaths.mapNotNull { path ->
                runCatching {
                    val reference = dataModel.getPropertyReferenceByName(path, context)
                    PinnedColumn(path = path, label = path, reference = reference)
                }.getOrNull()
            }
        }
    }
    val sortOptions = remember(dataModel) { buildSortOptions(dataModel) }
    var sortExpanded by remember { mutableStateOf(false) }
    var selectedSort by remember(state.selectedModelId) { mutableStateOf(sortOptions.firstOrNull()) }
    var sortDescending by remember(state.selectedModelId) { mutableStateOf(false) }
    val horizontalScroll = rememberScrollState()
    val indexColumnWidths = remember { mutableStateListOf<Dp>() }
    val densityHeight = when (uiState.gridDensity) {
        GridDensity.COMPACT -> 40.dp
        GridDensity.STANDARD -> 58.dp
        GridDensity.COMFY -> 70.dp
    }

    LaunchedEffect(indexColumns) {
        indexColumnWidths.clear()
        repeat(indexColumns.size) { indexColumnWidths.add(indexColumnWidth) }
    }

    LaunchedEffect(state.scanGeneration) {
        uiState.selectedRowKeys.clear()
    }

    LaunchedEffect(listState, uiState.resultsTab) {
        if (uiState.resultsTab != ResultsTab.DATA) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 10 && state.canLoadMoreScanResults()) {
                    state.loadMoreScanResults()
                }
            }
    }

    LaunchedEffect(sortOptions, state.scanConfig.orderFields) {
        val (paths, descending) = parseOrderFields(state.scanConfig.orderFields)
        val match = sortOptions.firstOrNull { it.orderPaths == paths }
        selectedSort = match ?: sortOptions.firstOrNull()
        sortDescending = descending ?: false
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
                    modifier = Modifier.fillMaxWidth(),
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

            if (uiState.resultsTab == ResultsTab.DATA && sortOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    val countSuffix = if (state.hasMoreScanResults()) "+" else ""
                    Text(
                        "${rows.size}$countSuffix records",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Sort by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        OutlinedButton(
                            onClick = { sortExpanded = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(24.dp),
                        ) {
                            Text(selectedSort?.label ?: "Key", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Open sort options", modifier = Modifier.size(14.dp))
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                            offset = DpOffset(0.dp, 4.dp),
                        ) {
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                                    onClick = {
                                        sortExpanded = false
                                        selectedSort = option
                                        applySortSelection(state, option, sortDescending)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val next = !sortDescending
                            sortDescending = next
                            selectedSort?.let { applySortSelection(state, it, next) }
                        },
                        enabled = selectedSort != null,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            if (sortDescending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (sortDescending) "Descending" else "Ascending",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        enabled = dataModel != null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add record", modifier = Modifier.size(12.dp))
                            Text(
                                "Add",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.offset(y = (-1).dp),
                            )
                        }
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
                                        rows.getOrNull(nextIndex)?.let { state.openRecord(it) }
                                        if (nextIndex >= rows.lastIndex && state.canLoadMoreScanResults()) {
                                            state.loadMoreScanResults()
                                        }
                                        ensureSelectionVisible(
                                            listState = listState,
                                            scope = listScope,
                                            index = nextIndex,
                                            movingDown = true,
                                            estimatedItemSizePx = with(density) { densityHeight.toPx().roundToInt() },
                                        )
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        rows.getOrNull(nextIndex)?.let { state.openRecord(it) }
                                        ensureSelectionVisible(
                                            listState = listState,
                                            scope = listScope,
                                            index = nextIndex,
                                            movingDown = false,
                                            estimatedItemSizePx = with(density) { densityHeight.toPx().roundToInt() },
                                        )
                                        true
                                    }
                                    Key.PageDown -> {
                                        val nextIndex = (selectedIndex + 10).coerceAtMost(rows.lastIndex)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        rows.getOrNull(nextIndex)?.let { state.openRecord(it) }
                                        if (nextIndex >= rows.lastIndex && state.canLoadMoreScanResults()) {
                                            state.loadMoreScanResults()
                                        }
                                        ensureSelectionVisible(
                                            listState = listState,
                                            scope = listScope,
                                            index = nextIndex,
                                            movingDown = true,
                                            estimatedItemSizePx = with(density) { densityHeight.toPx().roundToInt() },
                                        )
                                        true
                                    }
                                    Key.PageUp -> {
                                        val nextIndex = (selectedIndex - 10).coerceAtLeast(0)
                                        updateSelection(rows, nextIndex, uiState, event, anchorIndex) { anchorIndex = it }
                                        rows.getOrNull(nextIndex)?.let { state.openRecord(it) }
                                        ensureSelectionVisible(
                                            listState = listState,
                                            scope = listScope,
                                            index = nextIndex,
                                            movingDown = false,
                                            estimatedItemSizePx = with(density) { densityHeight.toPx().roundToInt() },
                                        )
                                        true
                                    }
                                    Key.Enter -> {
                                        val selected = rows.getOrNull(selectedIndex)
                                        if (selected != null) {
                                            state.openRecord(selected)
                                        }
                                        true
                                    }
                                    Key.Spacebar -> {
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
                                indexColumns = indexColumns,
                                indexWidths = indexColumnWidths,
                                pinnedColumns = pinnedColumns,
                                valuesWidth = valuesColumnWidth,
                                horizontalScroll = horizontalScroll,
                                onResizeKey = { delta ->
                                    keyColumnWidth = (keyColumnWidth + delta).coerceIn(140.dp, maxKeyWidth)
                                },
                                onResizeIndex = { index, delta ->
                                    val current = indexColumnWidths.getOrNull(index) ?: indexColumnWidth
                                    val next = (current + delta).coerceIn(120.dp, 260.dp)
                                    if (index < indexColumnWidths.size) {
                                        indexColumnWidths[index] = next
                                    }
                                },
                                onUnpin = { path ->
                                    uiState.togglePinned(state.selectedModelId, path)
                                },
                            )
                        }
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            val selected = uiState.selectedRowKeys[row.key] == true
                            ContextMenuArea(
                                items = {
                                    resultRowContextItems(
                                        onEdit = {
                                            if (state.currentDataModel() != null) {
                                                editRow = row
                                            }
                                        },
                                        onCopyRowJson = {
                                            val model = state.currentDataModel() ?: return@resultRowContextItems
                                            val json = serializeValuesToJson(model, row.values, buildRequestContext(model))
                                            clipboard.setText(AnnotatedString(json))
                                        },
                                        onCopyRowYaml = {
                                            val model = state.currentDataModel() ?: return@resultRowContextItems
                                            val yaml = serializeValuesToYaml(model, row.values, buildRequestContext(model))
                                            clipboard.setText(AnnotatedString(yaml))
                                        },
                                        onCopyKey = { clipboard.setText(AnnotatedString(row.keyText)) },
                                        onExportRow = {
                                            state.requestExportRowDialog(row)
                                        },
                                        onDelete = {
                                            deleteRow = row
                                            hardDelete = false
                                        },
                                    )
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
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .horizontalScroll(horizontalScroll),
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
                                    pinnedColumns.forEach { column ->
                                        val value = formatValueForPinned(row.values, column)
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(pinnedColumnWidth),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    indexColumns.forEachIndexed { colIndex, column ->
                                        val value = formatValueForColumn(row.values, column)
                                        val width = indexColumnWidths.getOrNull(colIndex) ?: indexColumnWidth
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(width),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        row.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(valuesColumnWidth),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
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
                    ModelTabPanel(state, uiState, modifier = Modifier.fillMaxHeight().fillMaxWidth())
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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
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
    if (showAddDialog && dataModel != null) {
        val initialValues = remember(dataModel) {
            @Suppress("UNCHECKED_CAST")
            (dataModel as TypedValuesDataModel<IsRootDataModel>).create(setDefaults = true) {}
        }
        RecordEditorDialog(
            state = state,
            mode = RecordEditorMode.ADD,
            dataModel = dataModel,
            initialValues = initialValues,
            initialKeyText = null,
            onDismiss = { showAddDialog = false },
        )
    }
    val editRowValue = editRow
    if (editRowValue != null && dataModel != null) {
        RecordEditorDialog(
            state = state,
            mode = RecordEditorMode.EDIT,
            dataModel = dataModel,
            initialValues = editRowValue.values,
            initialKeyText = editRowValue.keyText,
            onDismiss = { editRow = null },
        )
    } else if (editRowValue != null && dataModel == null) {
        editRow = null
    }
}

@Composable
private fun GridHeaderRow(
    keyWidth: Dp,
    indexColumns: List<IndexColumn>,
    indexWidths: List<Dp>,
    pinnedColumns: List<PinnedColumn>,
    valuesWidth: Dp,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    onResizeKey: (Dp) -> Unit,
    onResizeIndex: (Int, Dp) -> Unit,
    onUnpin: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp)
                .horizontalScroll(horizontalScroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                "Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(keyWidth),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ColumnResizeHandle(onResize = onResizeKey)
            pinnedColumns.forEach { column ->
                PinnedHeaderCell(
                    label = column.label,
                    width = pinnedColumnWidth,
                    onUnpin = { onUnpin(column.path) },
                )
            }
            indexColumns.forEachIndexed { index, column ->
                val width = indexWidths.getOrNull(index) ?: indexColumnWidth
                HeaderCell(column.label, width)
                ColumnResizeHandle(onResize = { delta -> onResizeIndex(index, delta) })
            }
            Text(
                "Values",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(valuesWidth),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderCell(label: String, width: Dp) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PinnedHeaderCell(
    label: String,
    width: Dp,
    onUnpin: () -> Unit,
) {
    Row(
        modifier = Modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUnpin, modifier = Modifier.size(18.dp)) {
            Icon(Icons.Filled.PushPin, contentDescription = "Unpin", modifier = Modifier.size(12.dp))
        }
    }
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

private fun resultRowContextItems(
    onEdit: () -> Unit,
    onCopyRowJson: () -> Unit,
    onCopyRowYaml: () -> Unit,
    onCopyKey: () -> Unit,
    onExportRow: () -> Unit,
    onDelete: () -> Unit,
): List<ContextMenuItem> = listOf(
    ContextMenuItem("Edit", onEdit),
    ContextMenuItem("Copy key", onCopyKey),
    ContextMenuItem("Copy data as JSON", onCopyRowJson),
    ContextMenuItem("Copy data as YAML", onCopyRowYaml),
    ContextMenuItem("Export row…", onExportRow),
    ContextMenuItem("Delete", onDelete),
)

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

private data class IndexColumn(
    val label: String,
    val reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
)

private data class PinnedColumn(
    val path: String,
    val label: String,
    val reference: AnyPropertyReference,
)

private data class SortOption(
    val label: String,
    val orderPaths: List<String>,
)

private fun buildIndexColumns(dataModel: IsRootDataModel?): List<IndexColumn> {
    if (dataModel == null) return emptyList()
    val references = collectIndexReferences(dataModel.Meta.keyDefinition) +
        dataModel.Meta.indexes.orEmpty().flatMap { collectIndexReferences(it) }
    val seen = linkedSetOf<String>()
    return references.mapNotNull { reference ->
        @Suppress("UNCHECKED_CAST")
        val castRef = reference as? IsPropertyReference<*, IsPropertyDefinition<*>, *> ?: return@mapNotNull null
        val label = referenceLabel(reference)
        if (!seen.add(label)) return@mapNotNull null
        IndexColumn(label = label, reference = castRef)
    }
}

private fun buildSortOptions(dataModel: IsRootDataModel?): List<SortOption> {
    if (dataModel == null) return emptyList()
    val options = mutableListOf<SortOption>()
    val keyRefs = collectIndexReferences(dataModel.Meta.keyDefinition)
    val keyPaths = if (keyRefs.isEmpty()) {
        listOf(KEY_ORDER_TOKEN)
    } else {
        keyRefs.mapNotNull { it as? AnyPropertyReference }.map { it.completeName }
    }
    options += SortOption(label = "Key", orderPaths = keyPaths)
    dataModel.Meta.indexes.orEmpty().forEachIndexed { index, indexable ->
        val refs = collectIndexReferences(indexable)
        val paths = refs.mapNotNull { it as? AnyPropertyReference }.map { it.completeName }
        val label = buildIndexSortLabel(index + 1, refs)
        options += SortOption(label = label, orderPaths = paths)
    }
    return options
}

private fun collectIndexReferences(indexable: IsIndexable): List<IsIndexablePropertyReference<*>> {
    return when (indexable) {
        is UUIDKey -> emptyList()
        is Multiple -> indexable.references
        is Reversed<*> -> listOf(indexable.reference)
        is ReferenceToMax<*> -> listOf(indexable.reference)
        is IsIndexablePropertyReference<*> -> listOf(indexable)
        else -> emptyList()
    }
}

private fun referenceLabel(reference: IsIndexablePropertyReference<*>): String {
    return (reference as? AnyPropertyReference)?.completeName ?: reference.toString()
}

private fun buildIndexSortLabel(
    index: Int,
    references: List<IsIndexablePropertyReference<*>>,
): String {
    if (references.isEmpty()) return "$index"
    val labels = references.map(::referenceLabel)
    val shown = labels.take(2)
    val suffix = if (labels.size > shown.size) "${shown.joinToString(", ")}…" else shown.joinToString(", ")
    return "$index • $suffix"
}

private fun formatValueForColumn(
    values: Values<IsRootDataModel>,
    column: IndexColumn,
): String {
    @Suppress("UNCHECKED_CAST")
    val reference = column.reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
    val value = values[reference]
    val text = formatValue(reference, value)
    return text.ifBlank { "—" }
}

private fun formatValueForPinned(
    values: Values<IsRootDataModel>,
    column: PinnedColumn,
): String {
    @Suppress("UNCHECKED_CAST")
    val reference = column.reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
    val value = values[reference]
    val text = formatValue(reference, value)
    return text.ifBlank { "—" }
}

private fun parseOrderFields(raw: String): Pair<List<String>, Boolean?> {
    if (raw.isBlank()) return emptyList<String>() to null
    val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList<String>() to null
    val paths = ArrayList<String>(tokens.size)
    val directions = ArrayList<Boolean>(tokens.size)
    for (token in tokens) {
        val (path, descending) = parseOrderToken(token)
        paths += path
        directions += descending
    }
    val direction = if (directions.all { it }) true else if (directions.all { !it }) false else null
    return paths to direction
}

private fun ensureSelectionVisible(
    listState: androidx.compose.foundation.lazy.LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
    index: Int,
    movingDown: Boolean,
    estimatedItemSizePx: Int,
) {
    if (index < 0) return
    val paddingPx = 15 // px padding; keep selected row at least this far from viewport edges
    scope.launch {
        val layoutInfo = listState.layoutInfo
        val totalCount = layoutInfo.totalItemsCount
        if (index >= totalCount) return@launch
        val visible = layoutInfo.visibleItemsInfo
        val itemInfo = visible.firstOrNull { it.index == index }

        // viewportStartOffset can be negative (content padding/sticky headers). Use viewport height for scrollToItem offsets.
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val viewportHeight = viewportEnd - viewportStart

        val itemSize = itemInfo?.size ?: estimatedItemSizePx
        val bottomAlignedOffset = (viewportHeight - paddingPx - itemSize).coerceAtLeast(0)
        val topAlignedOffset = paddingPx.coerceAtLeast(0)

        // If the item isn't currently laid out, jump it into view at the preferred edge.
        if (itemInfo == null) {
            listState.scrollToItem(index, if (movingDown) bottomAlignedOffset else topAlignedOffset)
            return@launch
        }

        val desiredStart = viewportStart + paddingPx
        val desiredEnd = viewportEnd - paddingPx
        val itemStart = itemInfo.offset
        val itemEnd = itemInfo.offset + itemInfo.size

        if (movingDown) {
            if (itemEnd > desiredEnd) {
                listState.scrollToItem(index, - (viewportHeight - (2 * itemSize) - 10))
            }
        } else {
            if (itemStart < desiredStart) {
                listState.scrollToItem(index, topAlignedOffset)
            }
        }
    }
}

private fun parseOrderToken(token: String): Pair<String, Boolean> {
    val trimmed = token.trim()
    return when {
        trimmed.startsWith("-") -> trimmed.drop(1).trim() to true
        trimmed.startsWith("+") -> trimmed.drop(1).trim() to false
        trimmed.endsWith(":desc", ignoreCase = true) -> trimmed.dropLast(5).trim() to true
        trimmed.endsWith(":asc", ignoreCase = true) -> trimmed.dropLast(4).trim() to false
        else -> trimmed to false
    }
}

private fun applySortSelection(
    state: BrowserState,
    option: SortOption,
    descending: Boolean,
) {
    val orderFields = if (option.orderPaths.isEmpty()) {
        ""
    } else {
        option.orderPaths.joinToString(", ") { path ->
            if (descending) "-$path" else path
        }
    }
    state.updateOrderFields(orderFields)
}
