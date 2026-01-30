package io.maryk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Browser(
    state: BrowserState,
    onClose: () -> Unit,
) {
    val uiState = remember { BrowserUiState() }
    var showExplainPanel by remember { mutableStateOf(false) }
    var appliedLastModel by remember { mutableStateOf(false) }
    var leftPanelWidth by remember { mutableStateOf(280.dp) }
    var rightPanelWidth by remember { mutableStateOf(360.dp) }

    LaunchedEffect(state.models, uiState.lastSelectedModelId) {
        if (!appliedLastModel) {
            val last = uiState.lastSelectedModelId
            if (last != null && state.models.any { it.id == last }) {
                state.selectModel(last)
            }
            appliedLastModel = true
        }
    }

    LaunchedEffect(state.selectedModelId) {
        uiState.setLastModel(state.selectedModelId)
    }

    LaunchedEffect(state.recordDetails) {
        if (state.recordDetails != null && !uiState.showInspector) {
            uiState.setInspectorOpen(true)
        }
    }

    AppScaffold(
        state = state,
        uiState = uiState,
        onToggleCatalog = { uiState.toggleCatalog() },
        onToggleInspector = { uiState.toggleInspector() },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val meta = event.isMetaPressed || event.isCtrlPressed
                        when {
                            meta && event.key == Key.R -> {
                                state.scanFromStart()
                                uiState.markRefreshed("Just now")
                                true
                            }
                            meta && event.key == Key.B -> {
                                uiState.toggleCatalog()
                                true
                            }
                            meta && event.key == Key.I -> {
                                uiState.toggleInspector()
                                true
                            }
                            meta && event.key == Key.One -> {
                                if (!uiState.showCatalog) {
                                    uiState.toggleCatalog()
                                }
                                true
                            }
                            meta && event.key == Key.O -> {
                                if (!uiState.showInspector) {
                                    uiState.toggleInspector()
                                }
                                true
                            }
                            event.key == Key.Escape -> {
                                when {
                                    uiState.showCatalog -> {
                                        uiState.toggleCatalog()
                                        true
                                    }
                                    uiState.showInspector -> {
                                        uiState.toggleInspector()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            else -> false
                        }
                    },
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxLeft = (maxWidth * 0.45f).coerceAtLeast(220.dp)
                    val maxRight = (maxWidth * 0.45f).coerceAtLeast(260.dp)
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (uiState.showCatalog) {
                            CatalogDrawer(state, modifier = Modifier.width(leftPanelWidth))
                            ResizableDivider(
                                end = true,
                                onDrag = { delta ->
                                    val next = (leftPanelWidth + delta).coerceIn(220.dp, maxLeft)
                                    leftPanelWidth = next
                                },
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(0.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ResultsDataGrid(
                                state = state,
                                uiState = uiState,
                                modifier = Modifier.weight(1f),
                            )
                            if (showExplainPanel) {
                                ExplainPanel(onClose = { showExplainPanel = false })
                            }
                        }
                        if (uiState.showInspector) {
                            ResizableDivider(
                                onDrag = { delta ->
                                    val next = (rightPanelWidth - delta).coerceIn(260.dp, maxRight)
                                    rightPanelWidth = next
                                },
                            )
                            InspectorDrawer(state, uiState, modifier = Modifier.width(rightPanelWidth))
                        }
                    }
                }
            }
        },
        bottomBar = {},
    )

    state.exportDialog?.let { request ->
        ExportFormatDialog(
            state = state,
            request = request,
            onDismiss = { state.clearExportDialog() },
        )
    }

    state.dataExportFormatDialog?.let { request ->
        DataExportFormatDialog(
            state = state,
            request = request,
            onDismiss = { state.clearDataExportFormatDialog() },
        )
    }

    state.dataExportDialog?.let { request ->
        ExportDataDialog(
            state = state,
            request = request,
            onDismiss = { state.clearDataExportDialog() },
        )
    }

    state.dataImportDialog?.let { request ->
        ImportDataDialog(
            state = state,
            _request = request,
            onDismiss = { state.clearDataImportDialog() },
        )
    }

    state.dataImportModelDialog?.let { request ->
        ImportModelDialog(
            state = state,
            request = request,
            onDismiss = { state.clearDataImportModelDialog() },
        )
    }

    if (state.showDeleteDialog) {
        DeleteDialog(state)
    }
}

@Composable
private fun ExportFormatDialog(
    state: BrowserState,
    request: ExportDialogRequest,
    onDismiss: () -> Unit,
) {
    val modelName = request.modelId?.let { id ->
        state.models.firstOrNull { it.id == id }?.name
    }
    var selected by remember(request.modelId) { mutableStateOf(ModelExportFormat.JSON) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (modelName == null) "Export all models" else "Export $modelName",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ModelExportFormat.entries.forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selected = format },
                    ) {
                        RadioButton(selected = selected == format, onClick = { selected = format })
                        Text(format.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Export",
                onClick = {
                    onDismiss()
                    val modelId = request.modelId
                    if (modelId == null) {
                        state.exportAllModels(selected)
                    } else {
                        state.exportModelById(modelId, selected)
                    }
                },
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun DataExportFormatDialog(
    state: BrowserState,
    request: DataExportFormatDialogRequest,
    onDismiss: () -> Unit,
) {
    val modelName = state.models.firstOrNull { it.id == request.modelId }?.name
    var selected by remember(request.modelId, request.scope) { mutableStateOf(DataExportFormat.JSON) }
    var includeVersionHistory by remember(request.modelId, request.scope) { mutableStateOf(false) }
    val title = when (request.scope) {
        DataExportScope.ROW -> "Export ${modelName ?: "row"}"
        DataExportScope.MODEL -> "Export ${modelName ?: "model"} data"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DataExportFormat.entries.forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selected = format },
                    ) {
                        RadioButton(selected = selected == format, onClick = { selected = format })
                        Text(format.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { includeVersionHistory = !includeVersionHistory },
                ) {
                    Checkbox(checked = includeVersionHistory, onCheckedChange = { includeVersionHistory = it })
                    Text("Include version history", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Export",
                onClick = {
                    onDismiss()
                    when (request.scope) {
                        DataExportScope.ROW -> {
                            val key = request.rowKey ?: return@ModalPrimaryButton
                            val keyText = request.rowKeyText ?: return@ModalPrimaryButton
                            state.exportRowDataByKey(request.modelId, key, keyText, selected, includeVersionHistory)
                        }
                        DataExportScope.MODEL -> {
                            state.exportModelData(request.modelId, selected, includeVersionHistory = includeVersionHistory)
                        }
                    }
                },
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ExportDataDialog(
    state: BrowserState,
    request: ExportDataDialogRequest,
    onDismiss: () -> Unit,
) {
    val models = state.models
    var exportAll by remember(request.defaultModelId) { mutableStateOf(request.defaultModelId == null) }
    var selectedModelId by remember(request.defaultModelId, models) {
        mutableStateOf(request.defaultModelId ?: models.firstOrNull()?.id)
    }
    var format by remember { mutableStateOf(DataExportFormat.JSON) }
    var includeVersionHistory by remember { mutableStateOf(false) }
    var folderPath by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val selectedModelName = models.firstOrNull { it.id == selectedModelId }?.name ?: "Select model"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export data", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scope", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { exportAll = true },
                    ) {
                        RadioButton(selected = exportAll, onClick = { exportAll = true })
                        Text("All data", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { exportAll = false },
                    ) {
                        RadioButton(selected = !exportAll, onClick = { exportAll = false })
                        Text("Specific model", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!exportAll) {
                        Box {
                            OutlinedButton(
                                onClick = { modelMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(selectedModelName, style = MaterialTheme.typography.bodySmall)
                            }
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                            ) {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.name, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            selectedModelId = model.id
                                            modelMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DataExportFormat.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { format = option },
                        ) {
                            RadioButton(selected = format == option, onClick = { format = option })
                            Text(option.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { includeVersionHistory = !includeVersionHistory },
                    ) {
                        Checkbox(checked = includeVersionHistory, onCheckedChange = { includeVersionHistory = it })
                        Text("Include version history", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = folderPath,
                        onValueChange = { folderPath = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Select a folder") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val picked = pickDirectory("Select export folder") ?: return@IconButton
                                    folderPath = picked
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.FolderOpen,
                                    contentDescription = "Browse folder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Export",
                onClick = {
                    val folder = folderPath.trim().ifEmpty {
                        pickDirectory("Select export folder") ?: return@ModalPrimaryButton
                    }
                    onDismiss()
                    if (exportAll) {
                        state.exportAllData(format, folder, includeVersionHistory)
                    } else {
                        val modelId = selectedModelId ?: return@ModalPrimaryButton
                        state.exportModelData(modelId, format, folder, includeVersionHistory)
                    }
                },
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ImportDataDialog(
    state: BrowserState,
    _request: ImportDataDialogRequest,
    onDismiss: () -> Unit,
) {
    var filePath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import data", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Select a file") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val picked = pickFile("Select import file") ?: return@IconButton
                                    filePath = picked
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.FileOpen,
                                    contentDescription = "Browse file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
                Text(
                    "Format, scope, and model are auto-detected from the file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Import",
                onClick = {
                    val path = filePath.trim().ifEmpty {
                        pickFile("Select import file") ?: return@ModalPrimaryButton
                    }
                    onDismiss()
                    state.startImportFromPath(path)
                },
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ImportModelDialog(
    state: BrowserState,
    request: ImportModelDialogRequest,
    onDismiss: () -> Unit,
) {
    val models = state.models
    var selectedModelId by remember(request.path, models) { mutableStateOf<UInt?>(null) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val selectedModelName = models.firstOrNull { it.id == selectedModelId }?.name ?: "Select model"
    val fileName = request.path.substringAfterLast('/').substringAfterLast('\\')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select model", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("File: $fileName", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(
                        onClick = { modelMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedModelName, style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    selectedModelId = model.id
                                    modelMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Import",
                enabled = selectedModelId != null,
                onClick = {
                    val modelId = selectedModelId ?: return@ModalPrimaryButton
                    onDismiss()
                    state.importData(modelId, request.format, request.scope, request.path)
                },
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ResizableDivider(
    onDrag: (Dp) -> Unit,
    end: Boolean = false
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxSize()
            .horizontalResizeCursor()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val delta = with(density) { dragAmount.x.toDp() }
                    onDrag(delta)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(if (!end) Alignment.CenterEnd else Alignment.CenterStart)
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun ExplainPanel(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explain / Plan", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close explain panel")
            }
        }
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            "Scan strategy: index scan → filter → projection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SurfaceCard("Index scan", "Using primary key index")
        SurfaceCard("Filter", "Applying where clause")
        SurfaceCard("Projection", "Return selected fields")
    }
}

@Composable
private fun SurfaceCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeleteDialog(state: BrowserState) {
    AlertDialog(
        onDismissRequest = { state.closeDeleteDialog() },
        title = { Text("Delete record", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This action is destructive. Continue?", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.pendingHardDelete,
                        onCheckedChange = { checked -> state.markPendingHardDelete(checked) },
                    )
                    Text("Hard delete", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            ModalPrimaryButton(
                label = "Delete",
                onClick = { state.deleteRecord(state.pendingHardDelete) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            )
        },
        dismissButton = {
            ModalSecondaryButton(label = "Cancel", onClick = { state.closeDeleteDialog() })
        },
    )
}
