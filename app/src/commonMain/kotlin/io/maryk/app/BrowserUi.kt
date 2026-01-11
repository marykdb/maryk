package io.maryk.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
                            InspectorDrawer(state, modifier = Modifier.width(rightPanelWidth))
                        }
                    }
                }
            }
        },
        bottomBar = {},
    )

    if (state.showDeleteDialog) {
        DeleteDialog(state)
    }
}

@Composable
private fun ResizableDivider(
    onDrag: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(6.dp)
            .fillMaxSize()
            .horizontalResizeCursor()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val delta = with(density) { dragAmount.x.toDp() }
                    onDrag(delta)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
        title = { Text("Delete record") },
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
            TextButton(onClick = { state.deleteRecord(state.pendingHardDelete) }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = { state.closeDeleteDialog() }) { Text("Cancel") }
        },
    )
}
