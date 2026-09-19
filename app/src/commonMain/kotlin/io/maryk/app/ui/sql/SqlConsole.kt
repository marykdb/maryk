package io.maryk.app.ui.sql

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.maryk.app.state.BrowserState
import io.maryk.app.state.SqlConsoleState
import io.maryk.app.state.SqlParameterInput
import io.maryk.app.state.SqlParameterKind
import io.maryk.app.state.sqlCellText
import io.maryk.app.ui.copyToClipboard
import maryk.sql.SqlRow
import maryk.sql.SqlType

@Composable
fun SqlConsoleDialog(browser: BrowserState, onDismiss: () -> Unit) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width = (with(density) { size.width.toDp() } - 48.dp).coerceIn(320.dp, 1180.dp)
    val height = (with(density) { size.height.toDp() } - 64.dp).coerceIn(480.dp, 860.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false,
    )) {
        SqlConsoleModalContent(browser, onDismiss, Modifier.width(width).height(height))
    }
}

@Composable
internal fun SqlConsoleModalContent(browser: BrowserState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SQL · ${browser.activeConnection?.definition?.name ?: "Store"}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close SQL") }
            }
            HorizontalDivider()
            SqlConsole(browser, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
fun SqlConsole(browser: BrowserState, modifier: Modifier = Modifier) {
    val state = browser.sqlConsole
    val editor = state.editor
    val tokens = remember(editor.text) { sqlEditorTokens(editor.text) }
    val parameterCount = tokens.count { it.style == SqlTokenStyle.PARAMETER }
    var showParameters by remember { mutableStateOf(false) }
    var explicitCompletion by remember { mutableStateOf(false) }
    var dismissCompletion by remember(editor.text, editor.selection) { mutableStateOf(false) }
    var selectedCompletion by remember(editor.text, editor.selection) { mutableStateOf(0) }
    val suggestions = remember(editor.text, editor.selection, state.catalog, state.running, explicitCompletion, dismissCompletion) {
        if (state.running || dismissCompletion || !editor.selection.collapsed) emptyList()
        else sqlCompletions(editor.text, editor.selection.start, state.catalog, explicitCompletion)
    }
    val suggestionScroll = rememberLazyListState()
    LaunchedEffect(selectedCompletion, suggestions) {
        if (suggestions.isNotEmpty()) suggestionScroll.animateScrollToItem(selectedCompletion.coerceAtMost(suggestions.lastIndex))
    }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val highlighting = remember(colors) { VisualTransformation { text ->
        TransformedText(buildAnnotatedString {
            append(text.text)
            sqlEditorTokens(text.text).forEach { token ->
                val color = when (token.style) {
                    SqlTokenStyle.KEYWORD -> colors.primary
                    SqlTokenStyle.FUNCTION -> colors.tertiary
                    SqlTokenStyle.STRING, SqlTokenStyle.NUMBER -> colors.secondary
                    SqlTokenStyle.COMMENT -> colors.onSurfaceVariant.copy(alpha = 0.65f)
                    SqlTokenStyle.PARAMETER -> colors.error
                    else -> colors.onSurface
                }
                addStyle(SpanStyle(color = color), token.start, token.end)
            }
        }, OffsetMapping.Identity)
    } }
    fun complete(index: Int) {
        suggestions.getOrNull(index)?.let { completion ->
            val (text, caret) = applySqlCompletion(editor.text, completion)
            state.updateEditor(TextFieldValue(text, TextRange(caret)))
            explicitCompletion = false
            dismissCompletion = true
            focus.requestFocus()
        }
    }
    val canRun = !state.running && state.catalog != null && editor.text.isNotBlank() &&
        (!browser.timeTravelEnabled || browser.timeTravelInputError == null)
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Button(onClick = { browser.runSql(parameterCount) }, enabled = canRun) { Text(if (state.writeMode) "Run write" else "Run query") }
            OutlinedButton(onClick = state::cancel, enabled = state.running) { Text("Cancel") }
            OutlinedButton(onClick = { browser.runSql(parameterCount, explain = true) }, enabled = canRun && !state.writeMode) { Text("Explain") }
            TextButton(onClick = { if (!showParameters) state.initializeParameters(parameterCount); showParameters = !showParameters }) { Text("Parameters ($parameterCount)") }
            val selectedTable = state.catalog?.tables?.firstOrNull { it.model === browser.activeConnection?.dataStore?.dataModelsById?.get(browser.selectedModelId) }
            TextButton(onClick = {
                selectedTable?.let { table ->
                    val columns = table.columns.filter { it.type != SqlType.ANY }.joinToString(", ") { column -> column.name.split('.').joinToString(".", transform = ::sqlIdentifier) }
                    val text = "SELECT $columns\nFROM ${sqlTableName(table)}\nLIMIT 100"
                    state.updateEditor(TextFieldValue(text, TextRange(text.length)))
                }
            }, enabled = !state.running && selectedTable != null) { Text("Use selected model") }
            Checkbox(checked = state.writeMode, onCheckedChange = { state.writeMode = it; if (it) { state.snapshot = false; state.includeDeleted = false } }, enabled = !state.running && !browser.timeTravelEnabled)
            Text("Enable writes")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Checkbox(checked = state.snapshot, onCheckedChange = { state.snapshot = it }, enabled = !state.running && !state.writeMode && state.supportsSnapshot && !browser.timeTravelEnabled)
            Text(if (browser.timeTravelEnabled) "Using time travel" else "Snapshot")
            Checkbox(checked = state.includeDeleted, onCheckedChange = { state.includeDeleted = it }, enabled = !state.running && !state.writeMode)
            Text("Include deleted")
            Text("  Ctrl/⌘+Enter: run · Ctrl+Space: complete", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (state.writeMode) Text("Writes change the connected store. Multi-row inserts can partly succeed.", color = colors.error, style = MaterialTheme.typography.bodySmall)
        if (showParameters && parameterCount > 0) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                items(parameterCount) { index -> ParameterEditor(state, index) }
            }
        }
        Column(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = editor,
                onValueChange = { if (it.text.length <= 65_536) { state.updateEditor(it); explicitCompletion = false } },
                readOnly = state.running,
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.Enter -> { if (canRun) browser.runSql(parameterCount); true }
                        event.isCtrlPressed && event.key == Key.Spacebar -> { explicitCompletion = true; dismissCompletion = false; true }
                        event.key == Key.Escape && suggestions.isNotEmpty() -> { dismissCompletion = true; explicitCompletion = false; true }
                        event.key == Key.DirectionDown && suggestions.isNotEmpty() -> { selectedCompletion = (selectedCompletion + 1) % suggestions.size; true }
                        event.key == Key.DirectionUp && suggestions.isNotEmpty() -> { selectedCompletion = (selectedCompletion + suggestions.size - 1) % suggestions.size; true }
                        event.key in setOf(Key.Tab, Key.Enter) && suggestions.isNotEmpty() -> { complete(selectedCompletion); true }
                        else -> false
                    }
                },
                label = { Text("SQL") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = highlighting,
                isError = state.errorPosition != null,
            )
            if (suggestions.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp)) {
                    LazyColumn(state = suggestionScroll) {
                        itemsIndexed(suggestions) { index, completion ->
                            Row(Modifier.fillMaxWidth().background(if (index == selectedCompletion) colors.secondaryContainer else colors.surface)
                                .clickable { complete(index) }.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(completion.label, fontFamily = FontFamily.Monospace, maxLines = 1)
                                Text(completion.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) { Text(error, color = colors.error, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                state.errorPosition?.let { position ->
                    val preceding = editor.text.take(position)
                    TextButton(onClick = { state.selectError(); focus.requestFocus() }) {
                        Text("Line ${preceding.count { it == '\n' } + 1}, column ${position - preceding.lastIndexOf('\n')}")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f).heightIn(max = 100.dp).verticalScroll(rememberScrollState())) { Text(state.message, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { copyToClipboard(sqlResultsTsv(state.columns.map { it.name }, state.rows)) }, enabled = state.columns.isNotEmpty()) { Text("Copy TSV") }
        }
        state.summary?.let { summary ->
            Text("${summary.status} · ${summary.storeRequests} requests · ${summary.fetchedRows} fetched · ${summary.expressionSteps} steps · ${summary.peakBufferedBytes / 1024} KiB buffer" +
                (summary.snapshotVersion?.let { " · version $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        SqlResults(state, Modifier.weight(0.55f).fillMaxWidth())
    }
}

@Composable
private fun ParameterEditor(state: SqlConsoleState, index: Int) {
    val input = state.parameters.getOrElse(index) { SqlParameterInput() }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("? ${index + 1}", Modifier.width(45.dp))
        Box {
            TextButton(onClick = { expanded = true }, enabled = !state.running) { Text(input.kind.name) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SqlParameterKind.entries.forEach { kind ->
                    DropdownMenuItem(text = { Text(kind.name) }, onClick = { state.updateParameter(index, input.copy(kind = kind)); expanded = false })
                }
            }
        }
        OutlinedTextField(value = input.text, onValueChange = { state.updateParameter(index, input.copy(text = it)) },
            singleLine = true, enabled = !state.running && input.kind != SqlParameterKind.NULL,
            label = { Text(if (input.kind == SqlParameterKind.TEXT) "Value (also for encoded keys and enum names)" else "Value") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SqlResults(state: SqlConsoleState, modifier: Modifier) {
    if (state.columns.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Results appear here", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val scroll = rememberScrollState()
    Box(modifier.horizontalScroll(scroll)) {
        Column(Modifier.width((state.columns.size * 200).dp).fillMaxSize()) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                state.columns.forEach { column ->
                    Column(Modifier.width(200.dp).padding(8.dp)) {
                        Text(column.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(column.type.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.rows) { _, row ->
                    SelectionContainer {
                        Row {
                            row.values.forEach { value -> Text(sqlCellText(value), Modifier.width(200.dp).padding(8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

internal fun sqlResultsTsv(columns: List<String>, rows: List<SqlRow>): String {
    fun escape(value: String) = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
    return (listOf(columns.joinToString("\t", transform = ::escape)) + rows.map { row -> row.values.joinToString("\t") { escape(sqlCellText(it)) } }).joinToString("\n")
}
