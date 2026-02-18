package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.maryk.app.data.KEY_ORDER_TOKEN
import io.maryk.app.data.ScanQueryParser
import io.maryk.app.data.buildRequestContext
import io.maryk.app.data.buildSummary
import io.maryk.app.data.serializeRecordToYaml
import io.maryk.app.ui.browser.InspectorData
import io.maryk.app.state.BrowserState
import io.maryk.app.state.BrowserUiState
import io.maryk.app.state.RecordDetails
import io.maryk.app.state.ScanRow
import io.maryk.app.ui.ModalPrimaryButton
import io.maryk.app.ui.ModalSecondaryButton
import io.maryk.app.ui.ModalSurface
import io.maryk.app.ui.handPointer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.ValuesCollectorContext
import maryk.core.models.asValues
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.Values
import kotlin.time.Instant
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReferenceEditor(
    label: String,
    path: String,
    definition: IsReferenceDefinition<*, *>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    state: BrowserState,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }
    var showPicker by remember(path) { mutableStateOf(false) }
    var showInfoDialog by remember(path) { mutableStateOf(false) }
    val dataModel = definition.dataModel
    var infoDetails by remember(path, text, state.activeConnection) { mutableStateOf<RecordDetails?>(null) }
    var infoLoading by remember(path, text, state.activeConnection) { mutableStateOf(false) }
    var infoError by remember(path, text, state.activeConnection) { mutableStateOf<String?>(null) }
    var tooltipPreview by remember(path, text, state.activeConnection) { mutableStateOf<String?>(null) }
    var tooltipLoading by remember(path, text, state.activeConnection) { mutableStateOf(false) }
    var tooltipError by remember(path, text, state.activeConnection) { mutableStateOf<String?>(null) }

    LaunchedEffect(text, state.activeConnection) {
        if (text.isBlank()) {
            tooltipLoading = false
            tooltipError = "No reference."
            tooltipPreview = null
            return@LaunchedEffect
        }
        val connection = state.activeConnection ?: run {
            tooltipLoading = false
            tooltipError = "No connection."
            tooltipPreview = null
            return@LaunchedEffect
        }
        val key = runCatching { dataModel.key(text) }.getOrNull() ?: run {
            tooltipLoading = false
            tooltipError = "Invalid key."
            tooltipPreview = null
            return@LaunchedEffect
        }
        tooltipLoading = true
        tooltipError = null
        val toVersion = state.currentTimeTravelVersion()
        val yaml = runCatching {
            connection.dataStore.execute(
                dataModel.get(
                    key,
                    toVersion = toVersion,
                    filterSoftDeleted = false,
                )
            ).values.firstOrNull()?.let { serializeRecordToYaml(dataModel, it) }
        }.getOrNull()
        tooltipLoading = false
        if (yaml == null) {
            tooltipPreview = null
            tooltipError = "Not found."
        } else {
            tooltipPreview = buildReferencePreviewYaml(yaml)
            tooltipError = null
        }
    }

    LaunchedEffect(showInfoDialog, text, state.activeConnection) {
        if (!showInfoDialog) return@LaunchedEffect
        if (text.isBlank()) {
            infoError = "No reference."
            infoDetails = null
            return@LaunchedEffect
        }
        val connection = state.activeConnection ?: return@LaunchedEffect
        val key = runCatching { dataModel.key(text) }.getOrNull() ?: run {
            infoError = "Invalid key."
            infoDetails = null
            return@LaunchedEffect
        }
        infoLoading = true
        infoError = null
        val toVersion = state.currentTimeTravelVersion()
        val result = runCatching {
            connection.dataStore.execute(
                dataModel.get(
                    key,
                    toVersion = toVersion,
                    filterSoftDeleted = false,
                )
            ).values.firstOrNull()
        }.getOrNull()
        infoLoading = false
        if (result == null) {
            infoDetails = null
            infoError = "Not found."
        } else {
            val yaml = serializeRecordToYaml(dataModel, result)
            infoDetails = RecordDetails(
                model = dataModel,
                key = key,
                keyText = key.toString(),
                firstVersion = result.firstVersion,
                lastVersion = result.lastVersion,
                isDeleted = result.isDeleted,
                values = result.values,
                yaml = yaml,
                editedYaml = yaml,
            )
        }
    }

    EditorTextRow(
        label = label,
        required = required,
        value = text,
        onValueChange = { next ->
            text = next
            if (next.isBlank()) {
                onValueChange(null)
                onError(if (required) "Required." else null)
                return@EditorTextRow
            }
            val parsed = parseValue(definition, next)
            if (parsed is ParseResult.Error) {
                onError(parsed.message)
            } else if (parsed is ParseResult.Value) {
                onValueChange(parsed.value)
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = "Reference key",
        error = error,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
        trailingContent = {
            InlineActionIcon(
                onClick = { showPicker = true },
                enabled = enabled,
                icon = Icons.Default.Search,
                contentDescription = "Pick reference",
                tooltipText = "Search reference",
            )
            InlineActionIcon(
                onClick = { showInfoDialog = true },
                enabled = true,
                icon = Icons.Default.Info,
                contentDescription = "Reference info",
                tooltipText = referenceInfoTooltipText(
                    referenceKeyText = text,
                    loading = tooltipLoading,
                    error = tooltipError,
                    preview = tooltipPreview,
                ),
            )
        },
    )

    if (showPicker) {
        ReferencePickerDialog(
            state = state,
            dataModel = dataModel,
            onDismiss = { showPicker = false },
            onPick = { keyText ->
                text = keyText
                val parsed = parseValue(definition, keyText)
                if (parsed is ParseResult.Value) {
                    onValueChange(parsed.value)
                    onError(validateValue(definition, parsed.value, required))
                } else if (parsed is ParseResult.Error) {
                    onError(parsed.message)
                }
                showPicker = false
            },
        )
    }

    if (showInfoDialog) {
        ReferenceInfoDialog(
            state = state,
            details = infoDetails,
            loading = infoLoading,
            error = infoError,
            onDismiss = { showInfoDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineActionIcon(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    tooltipText: String? = null,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .handPointer(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tooltipText.isNullOrBlank()) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        } else {
            TooltipArea(
                tooltip = { EditorTooltipCard(tooltipText) },
                delayMillis = 500,
                tooltipPlacement = TooltipPlacement.CursorPoint(
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(12.dp, 8.dp),
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(14.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorTooltipCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(max = 320.dp),
        )
    }
}

internal fun referenceInfoTooltipText(
    referenceKeyText: String,
    loading: Boolean,
    error: String?,
    preview: String?,
): String = when {
    referenceKeyText.isBlank() -> "No reference."
    loading -> "Loading…"
    !error.isNullOrBlank() -> error
    preview.isNullOrBlank() -> "Not found."
    else -> preview
}

internal fun buildReferencePreviewYaml(yaml: String, maxLines: Int = 10): String {
    val lines = yaml.lines()
    if (lines.size <= maxLines) return yaml
    return lines.take(maxLines).joinToString(separator = "\n") + "\n…"
}

@Composable
private fun ReferenceInfoDialog(
    state: BrowserState,
    details: RecordDetails?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    val uiState = remember { BrowserUiState() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(12.dp).widthIn(min = 480.dp, max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp).handPointer()) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                    }
                }
                when {
                    loading -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    details == null -> Text("Not found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Box(modifier = Modifier.heightIn(min = 320.dp, max = 560.dp)) {
                        InspectorData(state, uiState, details, showEdit = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferencePickerDialog(
    state: BrowserState,
    dataModel: IsRootDataModel,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ScanRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val sortOptions = remember(dataModel) { buildPickerSortOptions(dataModel) }
    var selectedSort by remember(dataModel) { mutableStateOf(sortOptions.firstOrNull()) }
    var sortDescending by remember(dataModel) { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val requestContext = remember(dataModel) { buildRequestContext(dataModel) }
    val sortStringRefs = remember(selectedSort, dataModel) {
        val option = selectedSort ?: return@remember emptyList()
        option.orderPaths.mapNotNull { path ->
            if (path == KEY_ORDER_TOKEN) return@mapNotNull null
            val reference = runCatching {
                dataModel.getPropertyReferenceByName(path, requestContext)
            }.getOrNull() ?: return@mapNotNull null
            if (reference.propertyDefinition is StringDefinition) reference else null
        }
    }

    LaunchedEffect(dataModel, state.activeConnection, selectedSort, sortDescending) {
        val connection = state.activeConnection ?: return@LaunchedEffect
        loading = true
        val orderTokens = selectedSort?.orderPaths?.map { path ->
            if (sortDescending) "-$path" else path
        }.orEmpty()
        val order = if (orderTokens.isEmpty()) null else ScanQueryParser.parseOrder(dataModel, orderTokens)
        val result = runCatching {
            val response = connection.dataStore.execute(
                dataModel.scan(
                    limit = 50u,
                    filterSoftDeleted = true,
                    order = order,
                    allowTableScan = true,
                )
            )
            response.values.map { valuesWithMeta ->
                ScanRow(
                    key = valuesWithMeta.key,
                    keyText = valuesWithMeta.key.toString(),
                    values = valuesWithMeta.values,
                    isDeleted = valuesWithMeta.isDeleted,
                    lastVersion = valuesWithMeta.lastVersion,
                    summary = buildSummary(
                        dataModel = dataModel,
                        values = valuesWithMeta.values,
                        displayFields = emptyList(),
                        requestContext = buildRequestContext(dataModel),
                        maxChars = 160,
                    ),
                )
            }
        }.getOrDefault(emptyList())
        rows = result
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(12.dp).widthIn(min = 420.dp, max = 700.dp)) {
                Text("Pick reference", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Sort by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.height(30.dp).widthIn(min = 140.dp).handPointer().clickable { sortExpanded = true },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    selectedSort?.label ?: "Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        selectedSort = option
                                        sortExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { sortDescending = !sortDescending },
                        modifier = Modifier.size(28.dp).handPointer(),
                        enabled = selectedSort != null,
                    ) {
                        Icon(
                            if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = "Toggle sort order",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                EditorTextField(
                    value = search,
                    onValueChange = { search = it },
                    enabled = true,
                    placeholder = "Search key, summary, or sort field",
                    isError = false,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val query = search.trim()
                val filtered = if (query.isBlank()) {
                    rows
                } else {
                    rows.filter { row ->
                        row.keyText.contains(query, true)
                            || row.summary.contains(query, true)
                            || sortStringRefs.any { ref ->
                            val value = resolvePropertyValue(ref, row.values)
                            value?.toString()?.contains(query, true) == true
                        }
                    }
                }
                if (loading) {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (filtered.isEmpty()) {
                    Text("No results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(scrollState)) {
                        filtered.forEach { row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .handPointer().clickable { onPick(row.keyText) }
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(row.keyText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                if (row.summary.isNotBlank()) {
                                    Text(
                                        row.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    ModalSecondaryButton(label = "Close", onClick = onDismiss)
                }
            }
        }
    }
}

private data class PickerSortOption(
    val label: String,
    val orderPaths: List<String>,
)

private fun buildPickerSortOptions(dataModel: IsRootDataModel): List<PickerSortOption> {
    val options = mutableListOf<PickerSortOption>()
    val keyRefs = collectIndexReferences(dataModel.Meta.keyDefinition)
    val keyPaths = if (keyRefs.isEmpty()) {
        listOf(KEY_ORDER_TOKEN)
    } else {
        keyRefs.mapNotNull { it as? AnyPropertyReference }.map { it.completeName }
    }
    options += PickerSortOption(label = "Key", orderPaths = keyPaths)
    dataModel.Meta.indexes.orEmpty().forEachIndexed { index, indexable ->
        val refs = collectIndexReferences(indexable)
        val paths = refs.mapNotNull { it as? AnyPropertyReference }.map { it.completeName }
        val label = buildIndexSortLabel(index + 1, refs)
        options += PickerSortOption(label = label, orderPaths = paths)
    }
    return options
}

private fun collectIndexReferences(indexable: IsIndexable): List<IsIndexablePropertyReference<*>> {
    return when (indexable) {
        is UUIDKey -> emptyList()
        is Multiple -> indexable.references.flatMap { collectIndexReferences(it) }
        is Reversed<*> -> listOf(indexable.reference)
        is ReferenceToMax<*> -> listOf(indexable.reference)
        is IsIndexablePropertyReference<*> -> listOf(indexable)
        else -> emptyList()
    }
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

private fun referenceLabel(reference: IsIndexablePropertyReference<*>): String {
    return (reference as? AnyPropertyReference)?.completeName ?: reference.toString()
}
