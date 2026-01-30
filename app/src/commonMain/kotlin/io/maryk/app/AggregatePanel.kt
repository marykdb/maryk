package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.ProvideVicoTheme
import com.patrykandpatrick.vico.multiplatform.m3.common.rememberM3VicoTheme
import kotlin.random.Random
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.aggregations.bucket.Bucket
import maryk.core.aggregations.bucket.DateHistogramResponse
import maryk.core.aggregations.bucket.EnumValuesResponse
import maryk.core.aggregations.bucket.TypesResponse
import maryk.core.aggregations.metric.AverageResponse
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.aggregations.metric.MinResponse
import maryk.core.aggregations.metric.StatsResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.types.DateUnit
import maryk.core.properties.references.IsPropertyReference

private val panelPadding = 8.dp
private val sectionSpacing = 8.dp
private val rowSpacing = 6.dp
private val compactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
private val tightButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
private val squaredButtonShape = RoundedCornerShape(4.dp)
private val inputMinHeight = 28.dp

@Composable
fun AggregateTabPanel(
    state: BrowserState,
    modifier: Modifier = Modifier,
) {
    val dataModel = state.currentDataModel()
    if (dataModel == null) {
        Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
            Text("No model selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val config = state.aggregationConfig
    val fields = remember(dataModel) { collectAggregationFields(dataModel) }
    val allPaths = remember(fields) { fields.map { it.path }.sorted() }
    var draft by remember(dataModel) { mutableStateOf(defaultDraft(fields)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCharts by remember { mutableStateOf(true) }
    var limitText by remember(config.limit) { mutableStateOf(config.limit.toString()) }
    var showBuilder by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
    ) {
        Column(
            modifier = Modifier.padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
        AggregateTopBar(
            limitText = limitText,
            onLimitChange = { text ->
                limitText = text
                val parsed = text.toIntOrNull()
                    if (parsed != null) {
                        state.updateAggregationLimit(parsed)
                    }
            },
            hasFilter = config.filterText.isNotBlank(),
            onFilterClick = { showFilterDialog = true },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        if (config.filterText.isNotBlank()) {
            FilterStatusBar(
                dataModel = dataModel,
                filterText = config.filterText,
                onEdit = { showFilterDialog = true },
                onClear = { state.updateAggregationFilterText("") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

            Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                Text("Configured aggregations", style = MaterialTheme.typography.titleSmall)
                if (config.definitions.isEmpty()) {
                    Text("Add an aggregation to get started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    config.definitions.forEach { definition ->
                        AggregationDefinitionRow(
                            definition = definition,
                            onEdit = {
                                editingId = definition.id
                                draft = draftFromDefinition(definition, fields)
                                showBuilder = true
                            },
                            onRemove = { state.removeAggregationDefinition(definition.id) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        editingId = null
                        draft = defaultDraft(fields)
                        showBuilder = true
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = squaredButtonShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.height(28.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add aggregation", modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add aggregation", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    squaredButtonShape,
                )
                .padding(horizontal = panelPadding, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.aggregationResult != null) {
                    OutlinedButton(
                        onClick = { state.clearAggregationResults() },
                        contentPadding = compactButtonPadding,
                        shape = squaredButtonShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        Text("Clear results", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { state.runAggregations() },
                    enabled = !state.isAggregating,
                    contentPadding = compactButtonPadding,
                    shape = squaredButtonShape,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run aggregations", modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isAggregating) "Running" else "Run", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Column(
            modifier = Modifier.padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            state.aggregationStatus?.let { status ->
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val response = state.aggregationResult
            val hasCharts = remember(response) {
                response?.namedAggregations?.values?.any { aggregation ->
                    when (aggregation) {
                        is DateHistogramResponse<*> -> aggregation.buckets.size > 1
                        is EnumValuesResponse<*> -> aggregation.buckets.size > 1
                        is TypesResponse<*> -> aggregation.buckets.size > 1
                        else -> false
                    }
                } == true
            }
            Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                    Text("Results", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    if (hasCharts) {
                        OutlinedButton(
                            onClick = { showCharts = !showCharts },
                            contentPadding = tightButtonPadding,
                            shape = squaredButtonShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        ) {
                            Text(if (showCharts) "Hide charts" else "Show charts", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (response == null) {
                    Text("Run the aggregation to see results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    response.namedAggregations.forEach { (name, aggregation) ->
                        AggregationResultCard(name, aggregation, showCharts)
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            dataModel = dataModel,
            initialFilterText = config.filterText,
            onApply = { yaml ->
                state.updateAggregationFilterText(yaml)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }

    if (showBuilder) {
        AggregateBuilderDialog(
            fields = fields,
            draft = draft,
            editingId = editingId,
            onDraftChange = { draft = it },
            onDismiss = {
                showBuilder = false
                editingId = null
            },
            onSave = { definition ->
                if (editingId == null) {
                    state.addAggregationDefinition(definition)
                } else {
                    state.updateAggregationDefinition(definition)
                }
                showBuilder = false
                editingId = null
            },
        )
    }
}

private data class AggregationDraft(
    val label: String,
    val bucket: AggregationBucket,
    val bucketField: String,
    val metric: AggregationMetric,
    val metricField: String,
    val dateUnit: DateUnit,
)

private fun defaultDraft(fields: List<AggregationField>): AggregationDraft {
    val firstPath = fields.firstOrNull()?.path.orEmpty()
    return AggregationDraft(
        label = "",
        bucket = AggregationBucket.NONE,
        bucketField = firstPath,
        metric = AggregationMetric.VALUE_COUNT,
        metricField = firstPath,
        dateUnit = DateUnit.Days,
    )
}

private fun draftFromDefinition(
    definition: AggregationDefinition,
    fields: List<AggregationField>,
): AggregationDraft {
    val fieldPaths = fields.map { it.path }
    val fallback = fieldPaths.firstOrNull().orEmpty()
    val metricField = if (definition.metricField in fieldPaths) definition.metricField else fallback
    val bucketField = if (definition.bucketField in fieldPaths) definition.bucketField else fallback
    return AggregationDraft(
        label = definition.label,
        bucket = definition.bucket,
        bucketField = if (bucketField.isBlank()) metricField else bucketField,
        metric = definition.metric,
        metricField = if (metricField.isBlank()) bucketField else metricField,
        dateUnit = definition.dateUnit,
    )
}

@Composable
private fun AggregateTopBar(
    limitText: String,
    onLimitChange: (String) -> Unit,
    hasFilter: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompactTextField(
            value = limitText,
            onValueChange = onLimitChange,
            placeholder = "500",
            modifier = Modifier.width(72.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onFilterClick,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            shape = squaredButtonShape,
            border = BorderStroke(
                1.dp,
                if (hasFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (hasFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.height(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.FilterAlt, contentDescription = "Filter", modifier = Modifier.size(12.dp))
                Text("Filter", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AggregateBuilderDialog(
    fields: List<AggregationField>,
    draft: AggregationDraft,
    editingId: String?,
    onDraftChange: (AggregationDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (AggregationDefinition) -> Unit,
) {
    val availableBuckets = remember(fields) {
        AggregationBucket.entries.filter { bucket ->
            bucket == AggregationBucket.NONE || fields.any { bucket.supports(it.definition) }
        }
    }
    val availableMetrics = remember(fields) {
        AggregationMetric.entries.filter { metric ->
            fields.any { metric.supports(it.definition) }
        }
    }
    val bucketFieldOptions = remember(draft.bucket, fields) {
        fields.filter { draft.bucket.supports(it.definition) }.map { it.path }
    }
    val metricFieldOptions = remember(draft.metric, fields) {
        fields.filter { draft.metric.supports(it.definition) }.map { it.path }
    }

    LaunchedEffect(availableBuckets, draft.bucket) {
        if (draft.bucket !in availableBuckets && availableBuckets.isNotEmpty()) {
            onDraftChange(draft.copy(bucket = availableBuckets.first()))
        }
    }
    LaunchedEffect(availableMetrics, draft.metric) {
        if (draft.metric !in availableMetrics && availableMetrics.isNotEmpty()) {
            onDraftChange(draft.copy(metric = availableMetrics.first()))
        }
    }
    LaunchedEffect(bucketFieldOptions, draft.bucketField, draft.bucket) {
        if (draft.bucket != AggregationBucket.NONE && draft.bucketField !in bucketFieldOptions && bucketFieldOptions.isNotEmpty()) {
            onDraftChange(draft.copy(bucketField = bucketFieldOptions.first()))
        }
    }
    LaunchedEffect(metricFieldOptions, draft.metricField) {
        if (draft.metricField !in metricFieldOptions && metricFieldOptions.isNotEmpty()) {
            onDraftChange(draft.copy(metricField = metricFieldOptions.first()))
        }
    }

    val canSave = fields.isNotEmpty() &&
        metricFieldOptions.isNotEmpty() &&
        draft.metricField.isNotBlank() &&
        (draft.bucket == AggregationBucket.NONE || (bucketFieldOptions.isNotEmpty() && draft.bucketField.isNotBlank()))

    ModalSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editingId == null) "Build aggregation" else "Edit aggregation",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close aggregation builder")
                }
            }
            if (fields.isEmpty()) {
                Text(
                    "No fields available for aggregation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactTextField(
                        value = draft.label,
                        onValueChange = { onDraftChange(draft.copy(label = it)) },
                        label = "Label",
                        placeholder = "Optional name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactDropdown(
                            label = "Bucket",
                            selection = draft.bucket.label,
                            options = availableBuckets.map { it.label },
                            onSelect = { selected ->
                                val bucket = availableBuckets.firstOrNull { it.label == selected } ?: draft.bucket
                                onDraftChange(draft.copy(bucket = bucket))
                            },
                            modifier = Modifier.width(200.dp),
                        )
                        if (draft.bucket != AggregationBucket.NONE) {
                            PathPicker(
                                label = "Bucket field",
                                value = draft.bucketField,
                                options = bucketFieldOptions,
                                onValueChange = { onDraftChange(draft.copy(bucketField = it)) },
                                modifier = Modifier.width(380.dp),
                            )
                            if (draft.bucket == AggregationBucket.DATE_HISTOGRAM) {
                                CompactDropdown(
                                    label = "Unit",
                                    selection = formatDateUnit(draft.dateUnit),
                                    options = DateUnit.entries.map { formatDateUnit(it) },
                                    onSelect = { selected ->
                                        val unit = DateUnit.entries.firstOrNull { formatDateUnit(it) == selected }
                                            ?: draft.dateUnit
                                        onDraftChange(draft.copy(dateUnit = unit))
                                    },
                                    modifier = Modifier.width(160.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactDropdown(
                            label = "Metric",
                            selection = draft.metric.label,
                            options = availableMetrics.map { it.label },
                            onSelect = { selected ->
                                val metric = availableMetrics.firstOrNull { it.label == selected } ?: draft.metric
                                onDraftChange(draft.copy(metric = metric))
                            },
                            modifier = Modifier.width(200.dp),
                        )
                        PathPicker(
                            label = "Metric field",
                            value = draft.metricField,
                            options = metricFieldOptions,
                            onValueChange = { onDraftChange(draft.copy(metricField = it)) },
                            modifier = Modifier.width(380.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                ModalSecondaryButton(label = "Cancel", onClick = onDismiss)
                ModalPrimaryButton(
                    label = if (editingId == null) "Add" else "Update",
                    enabled = canSave,
                    onClick = {
                        val metricField = draft.metricField.ifBlank { draft.bucketField }.trim()
                        val bucketField = if (draft.bucket == AggregationBucket.NONE) metricField else draft.bucketField.trim()
                        val definition = AggregationDefinition(
                            id = editingId ?: newAggregationId(),
                            label = draft.label.trim(),
                            bucket = draft.bucket,
                            bucketField = bucketField,
                            metric = draft.metric,
                            metricField = metricField,
                            dateUnit = draft.dateUnit,
                        )
                        onSave(definition)
                    },
                )
            }
        }
    }
}

private fun formatDateUnit(unit: DateUnit): String {
    val lower = unit.name.lowercase()
    return lower.replaceFirstChar { it.uppercase() }
}

@Composable
private fun CompactDropdown(
    label: String,
    selection: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(inputMinHeight)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    .clickable(enabled = options.isNotEmpty()) { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(inputMinHeight).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        selection.ifBlank { "Select" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Open options", modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Surface(
            shape = shape,
            color = colors.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(inputMinHeight)
                .border(1.dp, colors.outline.copy(alpha = 0.4f), shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(inputMinHeight)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (value.isBlank() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (trailing != null) {
                    trailing()
                }
            }
        }
    }
}

@Composable
private fun PathPicker(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Open options", modifier = Modifier.size(16.dp))
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, offset = DpOffset(0.dp, 4.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun AggregationDefinitionRow(
    definition: AggregationDefinition,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = definition.label.ifBlank { "Aggregation" }
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(
                    formatDefinitionDetail(definition),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit aggregation", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove aggregation", modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatDefinitionDetail(definition: AggregationDefinition): String {
    val metricPart = "${definition.metric.label} ${definition.metricField}".trim()
    return when (definition.bucket) {
        AggregationBucket.NONE -> metricPart
        AggregationBucket.DATE_HISTOGRAM -> {
            val unit = definition.dateUnit.name.lowercase().replaceFirstChar { it.uppercase() }
            "Group ${definition.bucketField} by $unit | $metricPart"
        }
        AggregationBucket.ENUM_VALUES -> "Group ${definition.bucketField} | $metricPart"
        AggregationBucket.TYPES -> "Group ${definition.bucketField} types | $metricPart"
    }
}

@Composable
private fun AggregationResultCard(
    name: String,
    response: IsAggregationResponse,
    showCharts: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall)
            when (response) {
                is ValueCountResponse<*> -> {
                    MetricRow("Count", response.value.toString())
                }
                is SumResponse<*> -> {
                    MetricRow("Sum", formatAggregationValue(response.reference, response.value))
                }
                is AverageResponse<*> -> {
                    MetricRow("Average", formatAggregationValue(response.reference, response.value))
                }
                is MinResponse<*> -> {
                    MetricRow("Min", formatAggregationValue(response.reference, response.value))
                }
                is MaxResponse<*> -> {
                    MetricRow("Max", formatAggregationValue(response.reference, response.value))
                }
                is StatsResponse<*> -> {
                    MetricRow("Count", response.valueCount.toString())
                    MetricRow("Sum", formatAggregationValue(response.reference, response.sum))
                    MetricRow("Average", formatAggregationValue(response.reference, response.average))
                    MetricRow("Min", formatAggregationValue(response.reference, response.min))
                    MetricRow("Max", formatAggregationValue(response.reference, response.max))
                }
                is DateHistogramResponse<*> -> {
                    BucketResults(
                        reference = toAnyReference(response.reference),
                        buckets = response.buckets,
                        showCharts = showCharts,
                    )
                }
                is EnumValuesResponse<*> -> {
                    BucketResults(
                        reference = toAnyReference(response.reference),
                        buckets = response.buckets,
                        showCharts = showCharts,
                    )
                }
                is TypesResponse<*> -> {
                    BucketResults(
                        reference = toAnyReference(response.reference),
                        buckets = response.buckets,
                        showCharts = showCharts,
                    )
                }
                else -> {
                    Text("Unsupported aggregation type.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BucketResults(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    buckets: List<Bucket<*>>,
    showCharts: Boolean,
) {
    if (buckets.isEmpty()) {
        Text("No buckets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val points = remember(reference, buckets) { buildBucketPoints(reference, buckets) }
    if (showCharts && points.size > 1) {
        BucketChart(points, modifier = Modifier.fillMaxWidth())
    }
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        buckets.forEach { bucket ->
            val keyLabel = formatBucketKey(reference, bucket.key)
            val metric = selectBucketMetric(bucket)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(keyLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${metric.label}: ${metric.value}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private data class BucketMetric(val label: String, val value: String, val numeric: Double?)

private fun selectBucketMetric(bucket: Bucket<*>): BucketMetric {
    val entry = bucket.aggregations.namedAggregations.entries.firstOrNull()
    if (entry == null) {
        return BucketMetric("Count", bucket.count.toString(), bucket.count.toDouble())
    }
    val value = aggregationValueForDisplay(entry.value)
    val numeric = aggregationNumericValue(entry.value)
    if (numeric == null) {
        return BucketMetric("Count", bucket.count.toString(), bucket.count.toDouble())
    }
    return BucketMetric(entry.key, value, numeric)
}

private fun aggregationValueForDisplay(response: IsAggregationResponse): String {
    return when (response) {
        is ValueCountResponse<*> -> response.value.toString()
        is SumResponse<*> -> formatAggregationValue(response.reference, response.value)
        is AverageResponse<*> -> formatAggregationValue(response.reference, response.value)
        is MinResponse<*> -> formatAggregationValue(response.reference, response.value)
        is MaxResponse<*> -> formatAggregationValue(response.reference, response.value)
        is StatsResponse<*> -> {
            formatAggregationValue(response.reference, response.average ?: response.sum ?: response.min ?: response.max)
        }
        else -> "-"
    }
}

private fun aggregationNumericValue(response: IsAggregationResponse): Double? {
    return when (response) {
        is ValueCountResponse<*> -> response.value.toDouble()
        is SumResponse<*> -> (response.value as? Number)?.toDouble()
        is AverageResponse<*> -> (response.value as? Number)?.toDouble()
        is MinResponse<*> -> (response.value as? Number)?.toDouble()
        is MaxResponse<*> -> (response.value as? Number)?.toDouble()
        is StatsResponse<*> -> {
            (response.average as? Number)?.toDouble()
                ?: (response.sum as? Number)?.toDouble()
                ?: (response.min as? Number)?.toDouble()
                ?: (response.max as? Number)?.toDouble()
        }
        else -> null
    }
}

private fun formatAggregationValue(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    value: Any?,
): String {
    return if (value == null) "-" else formatValue(toAnyReference(reference), value)
}

private fun formatBucketKey(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    key: Any?,
): String {
    return if (key == null) "-" else formatValue(toAnyReference(reference), key)
}

private fun newAggregationId(): String {
    return "agg-${Random.nextInt().toUInt().toString(16)}"
}

@Suppress("UNCHECKED_CAST")
private fun toAnyReference(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
): IsPropertyReference<Any, IsPropertyDefinition<Any>, *> {
    return reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
}

private data class BucketPoint(val label: String, val value: Double)

private fun buildBucketPoints(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    buckets: List<Bucket<*>>,
): List<BucketPoint> {
    return buckets.mapNotNull { bucket ->
        val keyLabel = formatBucketKey(reference, bucket.key)
        val metric = selectBucketMetric(bucket)
        val numeric = metric.numeric ?: return@mapNotNull null
        BucketPoint(keyLabel, numeric)
    }
}

@Composable
private fun BucketChart(
    points: List<BucketPoint>,
    modifier: Modifier = Modifier,
) {
    val labels = remember(points) { points.map { it.label } }
    val values = remember(points) { points.map { it.value } }
    val modelProducer = remember { CartesianChartModelProducer() }
    val formatter = remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt()
            labels.getOrNull(index).orEmpty()
        }
    }
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            columnSeries { series(values) }
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = formatter,
                    labelRotationDegrees = if (labels.any { it.length > 8 }) 45f else 0f,
                    guideline = null,
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier.height(180.dp),
        )
    }
}
