package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.yaml.MarykYamlReader
import maryk.lib.exceptions.ParseException

internal enum class FilterGroupType(val label: String) {
    AND("And"),
    OR("Or"),
}

internal enum class FilterOperator(
    val label: String,
    val requiresValue: Boolean = true,
    val requiresSecondValue: Boolean = false,
    val allowsMultipleValues: Boolean = false,
    val requiresComparable: Boolean = false,
) {
    EQUALS("Equals"),
    GREATER_THAN("Greater than", requiresComparable = true),
    GREATER_THAN_EQUALS("Greater than or equal", requiresComparable = true),
    LESS_THAN("Less than", requiresComparable = true),
    LESS_THAN_EQUALS("Less than or equal", requiresComparable = true),
    PREFIX("Prefix"),
    REGEX("Regex"),
    RANGE("Range", requiresSecondValue = true, requiresComparable = true),
    VALUE_IN("Value in", allowsMultipleValues = true),
    EXISTS("Exists", requiresValue = false),
}

private enum class FilterTab(val label: String) {
    UI("Builder"),
    RAW("Raw"),
}

private val compactFieldHeight = 30.dp

private sealed interface FilterNodeState {
    val id: Int
    var negated: Boolean
}

private class FilterGroupState(
    override val id: Int,
    type: FilterGroupType = FilterGroupType.AND,
    negated: Boolean = false,
) : FilterNodeState {
    var type by mutableStateOf(type)
    override var negated by mutableStateOf(negated)
    val children = mutableStateListOf<FilterNodeState>()
}

private class FilterConditionState(
    override val id: Int,
    path: String = "",
    operator: FilterOperator = FilterOperator.EQUALS,
    value: String = "",
    secondValue: String = "",
    values: String = "",
    negated: Boolean = false,
) : FilterNodeState {
    var path by mutableStateOf(path)
    var operator by mutableStateOf(operator)
    var value by mutableStateOf(value)
    var secondValue by mutableStateOf(secondValue)
    var values by mutableStateOf(values)
    override var negated by mutableStateOf(negated)
}

private data class ConditionValidation(
    val error: String?,
    val yaml: String?,
)

private data class FilterPathInfo(
    val paths: List<String>,
    val children: Map<String, List<String>>,
    val definitions: Map<String, IsPropertyDefinition<*>>,
)

private data class FilterChipData(
    val text: String,
    val isOperator: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterStatusBar(
    dataModel: IsRootDataModel?,
    filterText: String,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = remember(filterText, dataModel) {
        buildFilterChips(filterText, dataModel)
    }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        tonalElevation = 0.5.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.FilterAlt,
                contentDescription = "Filter",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            if (chips.isEmpty()) {
                Text(
                    "No filter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chips.forEach { chip ->
                            FilterChip(
                                text = chip.text,
                                isOperator = chip.isOperator,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit filter", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun FilterDialog(
    dataModel: IsRootDataModel,
    initialFilterText: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = remember(dataModel) { buildRequestContext(dataModel) }
    val pathInfo = remember(dataModel) { collectFilterPathInfo(dataModel) }
    val rootGroup = remember { FilterGroupState(id = 0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(1) }
    var activeTab by remember { mutableStateOf(FilterTab.UI) }
    var rawText by remember { mutableStateOf("") }

    fun updateRootGroup(parsed: FilterGroupState) {
        rootGroup.type = parsed.type
        rootGroup.negated = parsed.negated
        rootGroup.children.clear()
        rootGroup.children.addAll(parsed.children)
    }

    fun newId(): Int {
        val id = nextId
        nextId += 1
        return id
    }

    fun addCondition(target: FilterGroupState) {
        target.children.add(FilterConditionState(id = newId()))
    }

    fun addGroup(target: FilterGroupState) {
        target.children.add(FilterGroupState(id = newId()))
    }

    LaunchedEffect(dataModel, initialFilterText) {
        errorMessage = null
        rootGroup.children.clear()
        rootGroup.type = FilterGroupType.AND
        rootGroup.negated = false
        nextId = 1
        rawText = initialFilterText
        activeTab = FilterTab.UI
        val parsed = parseFilterRoot(dataModel, initialFilterText, ::newId)
        if (parsed == null) {
            if (initialFilterText.isNotBlank()) {
                errorMessage = "Existing filter too complex; start from scratch."
            }
            addCondition(rootGroup)
        } else {
            rootGroup.type = parsed.type
            rootGroup.negated = parsed.negated
            rootGroup.children.addAll(parsed.children)
            if (rootGroup.children.isEmpty()) {
                addCondition(rootGroup)
            }
        }
    }

    val validationMap by remember {
        derivedStateOf { buildValidationMap(dataModel, context, rootGroup) }
    }
    val hasErrors = validationMap.values.any { it.error != null }
    val yaml = buildNodeYaml(rootGroup, validationMap, isRoot = true)
    val canApply = !hasErrors && (!yaml.isNullOrBlank() || rawText.isNotBlank())

    LaunchedEffect(activeTab, yaml) {
        if (activeTab == FilterTab.UI) {
            rawText = yaml.orEmpty()
        }
    }

    ModalSurface(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter data", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close filter")
                }
            }
            if (errorMessage != null) {
                Text(
                    errorMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TabRow(selectedTabIndex = activeTab.ordinal, modifier = Modifier.height(28.dp)) {
                FilterTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(27.dp),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (activeTab == FilterTab.UI) {
                Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterGroupEditor(
                            group = rootGroup,
                            pathInfo = pathInfo,
                            validationMap = validationMap,
                            onAddCondition = ::addCondition,
                            onAddGroup = ::addGroup,
                            onRemoveGroup = null,
                            indent = 0,
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { updated ->
                        rawText = updated
                        var localNextId = 1
                        val parsed = parseFilterRoot(dataModel, updated, {
                            val id = localNextId
                            localNextId += 1
                            id
                        })
                        if (parsed != null) {
                            updateRootGroup(parsed)
                            nextId = localNextId
                        }
                    },
                    placeholder = { Text("YAML filter") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    textStyle = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                ModalSecondaryButton(
                    label = "Cancel",
                    onClick = onDismiss,
                )
                ModalPrimaryButton(
                    label = "Apply",
                    enabled = canApply,
                    onClick = {
                        if (activeTab == FilterTab.RAW) {
                            val raw = rawText.trim()
                            if (raw.isBlank()) {
                                onApply("")
                                return@ModalPrimaryButton
                            }
                            val parsed = runCatching { ScanQueryParser.parseFilter(dataModel, raw) }.getOrNull()
                            if (parsed == null) {
                                errorMessage = "Invalid YAML filter."
                                return@ModalPrimaryButton
                            }
                            onApply(raw)
                            return@ModalPrimaryButton
                        }
                        val output = yaml
                        if (output.isNullOrBlank()) {
                            errorMessage = "Define at least one filter condition."
                            return@ModalPrimaryButton
                        }
                        onApply(output)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterGroupEditor(
    group: FilterGroupState,
    pathInfo: FilterPathInfo,
    validationMap: Map<Int, ConditionValidation>,
    onAddCondition: (FilterGroupState) -> Unit,
    onAddGroup: (FilterGroupState) -> Unit,
    onRemoveGroup: ((FilterGroupState) -> Unit)?,
    indent: Int,
) {
    val paddingStart = (indent * 10).dp
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().padding(start = paddingStart),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (group.children.size > 1) {
                    GroupTypeToggle(group)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group.children.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Checkbox(checked = group.negated, onCheckedChange = { group.negated = it })
                            Text("Not", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onRemoveGroup != null) {
                        IconButton(onClick = { onRemoveGroup(group) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove group", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.children.forEach { child ->
                    when (child) {
                        is FilterConditionState -> {
                            FilterConditionRow(
                                condition = child,
                                pathInfo = pathInfo,
                                error = validationMap[child.id]?.error,
                                onRemove = { group.children.remove(child) },
                                indent = indent + 1,
                            )
                        }
                        is FilterGroupState -> {
                            FilterGroupEditor(
                                group = child,
                                pathInfo = pathInfo,
                                validationMap = validationMap,
                                onAddCondition = onAddCondition,
                                onAddGroup = onAddGroup,
                                onRemoveGroup = { group.children.remove(child) },
                                indent = indent + 1,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                SquaredIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Add condition",
                    onClick = { onAddCondition(group) },
                    size = 28.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    background = MaterialTheme.colorScheme.surface,
                )
                SquaredIconButton(
                    icon = Icons.Default.LibraryAdd,
                    contentDescription = "Add group",
                    onClick = { onAddGroup(group) },
                    size = 28.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    background = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@Composable
private fun GroupTypeToggle(group: FilterGroupState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterGroupType.entries.forEach { type ->
            val selected = group.type == type
            val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
            Text(
                type.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(background, RoundedCornerShape(6.dp))
                    .clickable { group.type = type }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FilterConditionRow(
    condition: FilterConditionState,
    pathInfo: FilterPathInfo,
    error: String?,
    onRemove: () -> Unit,
    indent: Int,
) {
    var operatorExpanded by remember(condition.id) { mutableStateOf(false) }
    var propertyExpanded by remember(condition.id) { mutableStateOf(false) }
    var valueExpanded by remember(condition.id) { mutableStateOf(false) }
    var propertyCursorToken by remember(condition.id) { mutableStateOf(0) }
    val baseDefinition = remember(condition.path, pathInfo) {
        pathInfo.definitions[condition.path.trim()]?.let(::unwrapDefinition)
    }
    val enumOptions = remember(baseDefinition) {
        (baseDefinition as? EnumDefinition<*>)?.enum?.cases()?.map { it.toString() }.orEmpty()
    }
    val isBoolean = baseDefinition is BooleanDefinition
    val propertySuggestions = remember(condition.path, pathInfo) {
        propertySuggestions(condition.path, pathInfo)
    }
    val valueSuggestions = remember(condition.path, condition.value, pathInfo, condition.operator) {
        valueSuggestions(condition.path, condition.value, pathInfo)
    }

    val paddingStart = (indent * 10).dp
    val rowShape = RoundedCornerShape(6.dp)
    Surface(
        shape = rowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), rowShape),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.weight(1.3f)) {
                    CompactField(
                        value = condition.path,
                        onValueChange = { updated ->
                            condition.path = updated
                            propertyExpanded = propertySuggestions(updated, pathInfo).isNotEmpty()
                        },
                        placeholder = "Field",
                        cursorToEndSignal = propertyCursorToken,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                propertyExpanded = focusState.isFocused && propertySuggestions.isNotEmpty()
                            },
                    )
                    DropdownMenu(
                        expanded = propertyExpanded && propertySuggestions.isNotEmpty(),
                        onDismissRequest = { propertyExpanded = false },
                        properties = PopupProperties(focusable = false),
                    ) {
                        propertySuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                onClick = {
                                    condition.path = suggestion
                                    propertyExpanded = false
                                    propertyCursorToken += 1
                                },
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { operatorExpanded = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.heightIn(min = compactFieldHeight),
                    ) {
                        Text(condition.operator.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (operatorExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Operator",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(expanded = operatorExpanded, onDismissRequest = { operatorExpanded = false }) {
                        FilterOperator.entries.forEach { operator ->
                            DropdownMenuItem(
                                text = { Text(operator.label, style = MaterialTheme.typography.labelSmall) },
                                onClick = {
                                    condition.operator = operator
                                    operatorExpanded = false
                                },
                            )
                        }
                    }
                }
                when {
                    condition.operator.requiresSecondValue -> {
                        if (baseDefinition is DateDefinition) {
                            CompactDatePickerField(
                                value = condition.value,
                                onValueChange = { condition.value = it },
                                placeholder = "From",
                                definition = baseDefinition,
                                modifier = Modifier.weight(0.8f),
                            )
                            CompactDatePickerField(
                                value = condition.secondValue,
                                onValueChange = { condition.secondValue = it },
                                placeholder = "To",
                                definition = baseDefinition,
                                modifier = Modifier.weight(0.8f),
                            )
                        } else {
                            CompactField(
                                value = condition.value,
                                onValueChange = { condition.value = it },
                                placeholder = "From",
                                modifier = Modifier.weight(0.8f),
                            )
                            CompactField(
                                value = condition.secondValue,
                                onValueChange = { condition.secondValue = it },
                                placeholder = "To",
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }
                    condition.operator.allowsMultipleValues -> {
                        CompactField(
                            value = condition.values,
                            onValueChange = { condition.values = it },
                            placeholder = "Values (comma)",
                            modifier = Modifier.weight(1.1f),
                        )
                    }
                    condition.operator.requiresValue -> {
                        when {
                            enumOptions.isNotEmpty() -> {
                                CompactSelectField(
                                    value = condition.value,
                                    options = enumOptions,
                                    placeholder = "Select",
                                    onSelect = { selection ->
                                        condition.value = selection
                                    },
                                    modifier = Modifier.weight(1.1f),
                                )
                            }
                            isBoolean -> {
                                CompactSelectField(
                                    value = condition.value,
                                    options = listOf("true", "false"),
                                    placeholder = "Select",
                                    onSelect = { selection ->
                                        condition.value = selection
                                    },
                                    modifier = Modifier.weight(1.1f),
                                )
                            }
                            baseDefinition is DateDefinition -> {
                                CompactDatePickerField(
                                    value = condition.value,
                                    onValueChange = { condition.value = it },
                                    placeholder = "Value",
                                    definition = baseDefinition,
                                    modifier = Modifier.weight(1.1f),
                                )
                            }
                            else -> {
                                Box(modifier = Modifier.weight(1.1f)) {
                                    CompactField(
                                        value = condition.value,
                                        onValueChange = { updated ->
                                            condition.value = updated
                                            valueExpanded = valueSuggestions(condition.path, updated, pathInfo).isNotEmpty()
                                        },
                                        placeholder = "Value",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                valueExpanded = focusState.isFocused && valueSuggestions.isNotEmpty()
                                            },
                                    )
                                    DropdownMenu(
                                        expanded = valueExpanded && valueSuggestions.isNotEmpty(),
                                        onDismissRequest = { valueExpanded = false },
                                        properties = PopupProperties(focusable = false),
                                    ) {
                                        valueSuggestions.forEach { suggestion ->
                                            DropdownMenuItem(
                                                text = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                                onClick = {
                                                    condition.value = suggestion
                                                    valueExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            "No value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.1f),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Checkbox(checked = condition.negated, onCheckedChange = { condition.negated = it })
                    Text("Not", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove condition", modifier = Modifier.size(16.dp))
                }
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    cursorToEndSignal: Int = 0,
    modifier: Modifier = Modifier,
    onFocusChanged: ((FocusState) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    val colors = MaterialTheme.colorScheme
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }

    LaunchedEffect(value, cursorToEndSignal) {
        if (fieldValue.text != value) {
            val selection = if (cursorToEndSignal > 0) {
                TextRange(value.length)
            } else {
                fieldValue.selection
            }
            fieldValue = TextFieldValue(value, selection)
        }
    }

    Surface(
        shape = shape,
        color = colors.surface,
        modifier = modifier
            .heightIn(min = compactFieldHeight)
            .border(1.dp, colors.outline.copy(alpha = 0.35f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = compactFieldHeight)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .let { base -> if (onFocusChanged != null) base.onFocusChanged(onFocusChanged) else base },
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    fieldValue = updated
                    onValueChange(updated.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isBlank()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompactSelectField(
    value: String,
    options: List<String>,
    placeholder: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.heightIn(min = compactFieldHeight),
        ) {
            Text(
                value.ifBlank { placeholder },
                style = MaterialTheme.typography.labelSmall,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Select value",
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.labelSmall) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    definition: DateDefinition,
    modifier: Modifier = Modifier,
) {
    val parsedDate = runCatching { definition.fromString(value.trim()) }.getOrNull()
    val initialMillis = parsedDate
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showPicker by remember(value) { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (initialMillis != null) {
            datePickerState.selectedDateMillis = initialMillis
        }
    }

    Row(
        modifier = modifier.heightIn(min = compactFieldHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { showPicker = true },
            modifier = Modifier.size(26.dp),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
        }
    }

    val selectedMillis = datePickerState.selectedDateMillis
    if (selectedMillis != null) {
        LaunchedEffect(selectedMillis) {
            val epochDays = floorDivLong(selectedMillis, 86_400_000L).toInt()
            val date = LocalDate.fromEpochDays(epochDays)
            onValueChange(definition.asString(date))
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                ModalPrimaryButton(label = "Done", onClick = { showPicker = false })
            },
            dismissButton = {
                ModalSecondaryButton(label = "Cancel", onClick = { showPicker = false })
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

private fun propertySuggestions(input: String, info: FilterPathInfo): List<String> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return info.children[""] ?: info.paths.take(12)
    val endsWithDot = trimmed.endsWith('.')
    if (!endsWithDot) {
        val directChildren = info.children[trimmed]
        if (!directChildren.isNullOrEmpty()) {
            return directChildren.map { child -> "$trimmed.$child" }.take(12)
        }
    }
    val basePath = trimmed.substringBeforeLast('.', "").takeIf { endsWithDot }.orEmpty()
    val segment = if (endsWithDot) "" else trimmed.substringAfterLast('.', trimmed)
    val parent = if (endsWithDot) trimmed.dropLast(1) else basePath
    val children = info.children[parent].orEmpty()
    if (children.isNotEmpty()) {
        return children.filter { it.startsWith(segment, ignoreCase = true) }
            .map { child -> if (parent.isBlank()) child else "$parent.$child" }
            .take(12)
    }
    return info.paths.filter { it.contains(trimmed, ignoreCase = true) }.take(12)
}

private fun valueSuggestions(path: String, input: String, info: FilterPathInfo): List<String> {
    val definition = info.definitions[path.trim()] ?: return emptyList()
    val suggestions = when (val base = unwrapDefinition(definition)) {
        is EnumDefinition<*> -> base.enum.cases().map { it.toString() }
        is BooleanDefinition -> listOf("true", "false")
        else -> emptyList()
    }
    val trimmed = input.trim()
    if (trimmed.isBlank()) return suggestions
    return suggestions.filter { it.startsWith(trimmed, ignoreCase = true) }
}

private fun unwrapDefinition(definition: IsPropertyDefinition<*>): IsPropertyDefinition<*> {
    return when (definition) {
        is IsValueDefinitionWrapper<*, *, *, *> -> definition.definition
        else -> definition
    }
}

private fun buildValidationMap(
    dataModel: IsRootDataModel,
    context: RequestContext,
    root: FilterGroupState,
): Map<Int, ConditionValidation> {
    val result = mutableMapOf<Int, ConditionValidation>()
    fun visit(node: FilterNodeState) {
        when (node) {
            is FilterConditionState -> result[node.id] = validateCondition(dataModel, context, node)
            is FilterGroupState -> node.children.forEach(::visit)
        }
    }
    visit(root)
    return result
}

private fun buildNodeYaml(
    node: FilterNodeState,
    validations: Map<Int, ConditionValidation>,
    isRoot: Boolean,
): String? {
    return when (node) {
        is FilterConditionState -> {
            val yaml = validations[node.id]?.yaml ?: return null
            if (node.negated) "!Not\n${formatListItem(yaml)}" else yaml
        }
        is FilterGroupState -> {
            val childYamls = node.children.mapNotNull { child -> buildNodeYaml(child, validations, isRoot = false) }
            if (childYamls.isEmpty()) return null
            val groupYaml = if (childYamls.size == 1) {
                childYamls.first()
            } else {
                val tag = if (node.type == FilterGroupType.AND) "!And" else "!Or"
                buildString {
                    append(tag)
                    childYamls.forEach { item ->
                        append("\n")
                        append(formatListItem(item))
                    }
                }
            }
            val output = if (node.negated) "!Not\n${formatListItem(groupYaml)}" else groupYaml
            if (isRoot) output.trim() else output
        }
    }
}

private fun formatListItem(item: String): String {
    val lines = item.trim().lines()
    if (lines.isEmpty()) return ""
    return buildString {
        append("- ")
        append(lines.first())
        if (lines.size > 1) {
            lines.drop(1).forEach { line ->
                append("\n  ")
                append(line)
            }
        }
    }
}

private fun validateCondition(
    dataModel: IsRootDataModel,
    context: RequestContext,
    condition: FilterConditionState,
): ConditionValidation {
    val path = condition.path.trim()
    if (path.isBlank()) return ConditionValidation("Select a field.", null)
    val reference = runCatching { dataModel.getPropertyReferenceByName(path, context) }.getOrNull()
        ?: return ConditionValidation("Unknown field.", null)

    if (condition.operator.requiresComparable) {
        val comparable = reference.propertyDefinition is IsComparableDefinition<*, *>
        if (!comparable) {
            return ConditionValidation("Field is not comparable.", null)
        }
    }

    if (!condition.operator.requiresValue) {
        val yaml = buildSimpleFilterYaml(FilterOperator.EXISTS, reference, emptyList())
        return ConditionValidation(null, yaml)
    }

    val serializable = resolveSerializableDefinition(reference.propertyDefinition)
        ?: return ConditionValidation("Field not serializable.", null)
    val referenceContext = context.also {
        @Suppress("UNCHECKED_CAST")
        it.reference = reference as? IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>
    }

    return when {
        condition.operator.requiresSecondValue -> {
            val firstRaw = condition.value.trim()
            val secondRaw = condition.secondValue.trim()
            if (firstRaw.isBlank() || secondRaw.isBlank()) {
                ConditionValidation("Both range values required.", null)
            } else {
                val firstResult = parseValue(serializable, referenceContext, firstRaw)
                val secondResult = parseValue(serializable, referenceContext, secondRaw)
                val error = firstResult.error ?: secondResult.error
                if (error != null) {
                    ConditionValidation(error, null)
                } else {
                    val yaml = buildSimpleFilterYaml(
                        condition.operator,
                        reference,
                        listOf(firstResult.value!!, secondResult.value!!),
                    )
                    ConditionValidation(null, yaml)
                }
            }
        }
        condition.operator.allowsMultipleValues -> {
            val tokens = splitTokens(condition.values)
            if (tokens.isEmpty()) {
                ConditionValidation("Provide one or more values.", null)
            } else {
                val parsedValues = mutableListOf<Any>()
                var error: String? = null
                for (token in tokens) {
                    val result = parseValue(serializable, referenceContext, token)
                    if (result.error != null) {
                        error = result.error
                        break
                    }
                    parsedValues.add(result.value!!)
                }
                if (error != null) {
                    ConditionValidation(error, null)
                } else {
                    val yaml = buildSimpleFilterYaml(condition.operator, reference, parsedValues)
                    ConditionValidation(null, yaml)
                }
            }
        }
        else -> {
            val raw = condition.value.trim()
            if (raw.isBlank()) {
                ConditionValidation("Value required.", null)
            } else {
                val parsed = parseValue(serializable, referenceContext, raw)
                if (parsed.error != null) {
                    ConditionValidation(parsed.error, null)
                } else {
                    if (condition.operator == FilterOperator.REGEX) {
                        val pattern = parsed.value?.toString().orEmpty()
                        runCatching { Regex(pattern) }.getOrElse {
                            return ConditionValidation("Invalid regex pattern.", null)
                        }
                    }
                    val yaml = buildSimpleFilterYaml(condition.operator, reference, listOf(parsed.value!!))
                    ConditionValidation(null, yaml)
                }
            }
        }
    }
}

private data class ParsedValueResult(val value: Any?, val error: String?)

private fun parseValue(
    serializable: IsSerializablePropertyDefinition<Any, IsPropertyContext>,
    context: RequestContext,
    raw: String,
): ParsedValueResult {
    return try {
        val reader = MarykYamlReader(raw)
        reader.nextToken()
        val value = serializable.readJson(reader, context)
        serializable.validateWithRef(null, value) { null }
        ParsedValueResult(value, null)
    } catch (exception: ParseException) {
        ParsedValueResult(null, exception.message ?: "Invalid value.")
    } catch (exception: Exception) {
        ParsedValueResult(null, exception.message ?: "Invalid value.")
    }
}

private fun buildSimpleFilterYaml(
    operator: FilterOperator,
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    values: List<Any>,
): String {
    return when (operator) {
        FilterOperator.EXISTS -> "!Exists ${reference.completeName}"
        FilterOperator.RANGE -> {
            val from = serializeFilterValue(reference, values.first())
            val to = serializeFilterValue(reference, values.last())
            "!Range { ${reference.completeName}: [$from, $to] }"
        }
        FilterOperator.VALUE_IN -> {
            val serialized = values.joinToString(", ") { serializeFilterValue(reference, it) }
            "!ValueIn { ${reference.completeName}: [$serialized] }"
        }
        else -> {
            val value = serializeFilterValue(reference, values.firstOrNull())
            val tag = when (operator) {
                FilterOperator.EQUALS -> "!Equals"
                FilterOperator.GREATER_THAN -> "!GreaterThan"
                FilterOperator.GREATER_THAN_EQUALS -> "!GreaterThanEquals"
                FilterOperator.LESS_THAN -> "!LessThan"
                FilterOperator.LESS_THAN_EQUALS -> "!LessThanEquals"
                FilterOperator.PREFIX -> "!Prefix"
                FilterOperator.REGEX -> "!RegEx"
                FilterOperator.EXISTS,
                FilterOperator.RANGE,
                FilterOperator.VALUE_IN -> "!Equals"
            }
            "$tag { ${reference.completeName}: $value }"
        }
    }
}

private fun serializeFilterValue(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    value: Any?,
): String = formatValue(reference, value)

private fun resolveSerializableDefinition(definition: IsPropertyDefinition<*>): IsSerializablePropertyDefinition<Any, IsPropertyContext>? {
    return when (definition) {
        is IsValueDefinitionWrapper<*, *, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition.definition as? IsSerializablePropertyDefinition<Any, IsPropertyContext>
        }
        is IsSerializablePropertyDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition as IsSerializablePropertyDefinition<Any, IsPropertyContext>
        }
        else -> null
    }
}

private fun splitTokens(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun FilterChip(text: String, isOperator: Boolean) {
    val colors = MaterialTheme.colorScheme
    val background = if (isOperator) {
        colors.primary.copy(alpha = 0.08f)
    } else {
        colors.primary.copy(alpha = 0.16f)
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SquaredIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    tint: Color,
    background: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(size),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp), tint = tint)
        }
    }
}

private fun buildFilterChips(
    filterText: String,
    dataModel: IsRootDataModel?,
): List<FilterChipData> {
    val raw = filterText.trim()
    if (raw.isBlank()) return emptyList()
    if (dataModel == null) {
        return buildFallbackChips(raw).ifEmpty { listOf(FilterChipData("Custom filter", isOperator = true)) }
    }
    val root = parseFilterRoot(dataModel, raw) { 0 }
        ?: return buildFallbackChips(raw).ifEmpty { listOf(FilterChipData("Custom filter", isOperator = true)) }
    val chips = mutableListOf<FilterChipData>()
    fun appendNode(node: FilterNodeState) {
        when (node) {
            is FilterConditionState -> chips.add(FilterChipData(formatConditionChip(node)))
            is FilterGroupState -> {
                val prefix = if (node.negated) listOf(FilterChipData("NOT", isOperator = true)) else emptyList()
                if (node.children.size > 1) {
                    chips.addAll(prefix)
                } else if (prefix.isNotEmpty()) {
                    chips.addAll(prefix)
                }
                node.children.forEachIndexed { index, child ->
                    if (index > 0) {
                        val op = if (node.type == FilterGroupType.AND) "AND" else "OR"
                        chips.add(FilterChipData(op, isOperator = true))
                    }
                    appendNode(child)
                }
            }
        }
    }
    appendNode(root)
    return chips
}

private fun buildFallbackChips(raw: String): List<FilterChipData> {
    val chips = mutableListOf<FilterChipData>()
    val regex = Regex("!(Equals|GreaterThanEquals|GreaterThan|LessThanEquals|LessThan|Prefix|RegEx|Range|ValueIn)\\s*\\{\\s*([^:}]+):\\s*([^}]+)\\}")
    val existsRegex = Regex("!Exists\\s+([^\\s]+)")
    regex.findAll(raw).forEach { match ->
        val op = match.groupValues[1]
        val path = match.groupValues[2].trim()
        val value = match.groupValues[3].trim()
        val label = when (op) {
            "Equals" -> "$path = $value"
            "GreaterThan" -> "$path > $value"
            "GreaterThanEquals" -> "$path >= $value"
            "LessThan" -> "$path < $value"
            "LessThanEquals" -> "$path <= $value"
            "Prefix" -> "$path starts $value"
            "RegEx" -> "$path matches $value"
            "Range" -> "$path ${value.trim('[', ']')}"
            "ValueIn" -> "$path in $value"
            else -> "$path $value"
        }
        chips.add(FilterChipData(label))
    }
    existsRegex.findAll(raw).forEach { match ->
        chips.add(FilterChipData("${match.groupValues[1]} exists"))
    }
    return chips
}

private fun formatConditionChip(condition: FilterConditionState): String {
    val prefix = if (condition.negated) "NOT " else ""
    val path = condition.path.ifBlank { "?" }
    return when (condition.operator) {
        FilterOperator.EQUALS -> "$prefix$path = ${condition.value}"
        FilterOperator.GREATER_THAN -> "$prefix$path > ${condition.value}"
        FilterOperator.GREATER_THAN_EQUALS -> "$prefix$path >= ${condition.value}"
        FilterOperator.LESS_THAN -> "$prefix$path < ${condition.value}"
        FilterOperator.LESS_THAN_EQUALS -> "$prefix$path <= ${condition.value}"
        FilterOperator.PREFIX -> "$prefix$path starts ${condition.value}"
        FilterOperator.REGEX -> "$prefix$path matches ${condition.value}"
        FilterOperator.RANGE -> "$prefix$path ${condition.value}..${condition.secondValue}"
        FilterOperator.VALUE_IN -> "$prefix$path in [${condition.values}]"
        FilterOperator.EXISTS -> "$prefix$path exists"
    }
}

private fun floorDivLong(value: Long, divisor: Long): Long {
    return if (value >= 0) {
        value / divisor
    } else {
        -((-value + divisor - 1) / divisor)
    }
}

private fun parseFilterRoot(
    dataModel: IsRootDataModel,
    raw: String,
    newId: () -> Int,
): FilterGroupState? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val filter = runCatching { ScanQueryParser.parseFilter(dataModel, trimmed) }.getOrNull() ?: return null
    val node = buildNodeFromFilter(filter, newId) ?: return null
    return when (node) {
        is FilterGroupState -> node
        is FilterConditionState -> {
            FilterGroupState(id = newId()).also { group ->
                group.children.add(node)
            }
        }
    }
}

private fun buildNodeFromFilter(
    filter: IsFilter,
    newId: () -> Int,
): FilterNodeState? {
    return when (filter) {
        is And -> FilterGroupState(id = newId(), type = FilterGroupType.AND).also { group ->
            filter.filters.mapNotNull { buildNodeFromFilter(it, newId) }.forEach(group.children::add)
        }
        is Or -> FilterGroupState(id = newId(), type = FilterGroupType.OR).also { group ->
            filter.filters.mapNotNull { buildNodeFromFilter(it, newId) }.forEach(group.children::add)
        }
        is Not -> {
            if (filter.filters.isEmpty()) return null
            if (filter.filters.size == 1) {
                val inner = buildNodeFromFilter(filter.filters.first(), newId) ?: return null
                inner.negated = !inner.negated
                inner
            } else {
                FilterGroupState(id = newId(), type = FilterGroupType.OR, negated = true).also { group ->
                    filter.filters.mapNotNull { buildNodeFromFilter(it, newId) }.forEach(group.children::add)
                }
            }
        }
        else -> {
            val conditions = parseSimpleConditions(filter, newId) ?: return null
            if (conditions.size == 1) conditions.first() else {
                FilterGroupState(id = newId(), type = FilterGroupType.AND).also { group ->
                    conditions.forEach(group.children::add)
                }
            }
        }
    }
}

private fun parseSimpleConditions(
    filter: IsFilter,
    newId: () -> Int,
): List<FilterConditionState>? {
    return when (filter) {
        is Equals -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.EQUALS, formatValue(pair.reference, pair.value))
        }
        is GreaterThan -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.GREATER_THAN, formatValue(pair.reference, pair.value))
        }
        is GreaterThanEquals -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.GREATER_THAN_EQUALS, formatValue(pair.reference, pair.value))
        }
        is LessThan -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.LESS_THAN, formatValue(pair.reference, pair.value))
        }
        is LessThanEquals -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.LESS_THAN_EQUALS, formatValue(pair.reference, pair.value))
        }
        is Prefix -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.PREFIX, formatValue(pair.reference, pair.value))
        }
        is RegEx -> filter.referenceValuePairs.map { pair ->
            conditionFromValue(newId(), pair.reference.completeName, FilterOperator.REGEX, pair.regex.pattern)
        }
        is Range -> filter.referenceValuePairs.map { pair ->
            FilterConditionState(
                id = newId(),
                path = pair.reference.completeName,
                operator = FilterOperator.RANGE,
                value = formatValue(pair.reference, pair.range.from),
                secondValue = formatValue(pair.reference, pair.range.to),
            )
        }
        is ValueIn -> filter.referenceValuePairs.map { pair ->
            FilterConditionState(
                id = newId(),
                path = pair.reference.completeName,
                operator = FilterOperator.VALUE_IN,
                values = pair.values.joinToString(", ") { value -> formatValue(pair.reference, value) },
            )
        }
        is Exists -> filter.references.map { reference ->
            FilterConditionState(id = newId(), path = reference.completeName, operator = FilterOperator.EXISTS)
        }
        else -> null
    }
}

private fun conditionFromValue(
    id: Int,
    path: String,
    operator: FilterOperator,
    value: String,
): FilterConditionState = FilterConditionState(
    id = id,
    path = path,
    operator = operator,
    value = value,
)

private fun collectFilterPathInfo(model: IsTypedDataModel<*>): FilterPathInfo {
    val children = mutableMapOf<String, MutableList<String>>()
    val definitions = mutableMapOf<String, IsPropertyDefinition<*>>()
    fun addChild(parent: String, child: String) {
        val list = children.getOrPut(parent) { mutableListOf() }
        if (child !in list) list.add(child)
    }
    lateinit var collectModelPaths: (IsTypedDataModel<*>, String) -> Unit
    lateinit var collectDefinitionPaths: (IsPropertyDefinition<*>, String) -> Unit
    collectModelPaths = { typedModel, prefix ->
        typedModel.forEach { wrapper ->
            val path = if (prefix.isBlank()) wrapper.name else "$prefix.${wrapper.name}"
            addChild(prefix, wrapper.name)
            definitions[path] = wrapper.definition
            collectDefinitionPaths(wrapper.definition, path)
        }
    }
    collectDefinitionPaths = { definition, parent ->
        when (definition) {
            is IsEmbeddedDefinition<*> -> collectModelPaths(definition.dataModel, parent)
            is ValueObjectDefinition<*, *> -> collectModelPaths(definition.dataModel, parent)
            is IsMultiTypeDefinition<*, *, *> -> {
                definition.typeEnum.cases().forEach { typeCase ->
                    val sub = definition.definition(typeCase.index) ?: return@forEach
                    val child = typeCase.index.toString()
                    val path = "$parent.$child"
                    addChild(parent, child)
                    definitions[path] = sub
                    collectDefinitionPaths(sub, path)
                }
            }
            is IsMapDefinition<*, *, *> -> {
                addChild(parent, "key")
                addChild(parent, "value")
                val keyPath = "$parent.key"
                val valuePath = "$parent.value"
                definitions[keyPath] = definition.keyDefinition
                definitions[valuePath] = definition.valueDefinition
                collectDefinitionPaths(definition.keyDefinition, keyPath)
                collectDefinitionPaths(definition.valueDefinition, valuePath)
            }
            is IsListDefinition<*, *> -> {
                addChild(parent, "value")
                val valuePath = "$parent.value"
                definitions[valuePath] = definition.valueDefinition
                collectDefinitionPaths(definition.valueDefinition, valuePath)
            }
            is IsSetDefinition<*, *> -> {
                addChild(parent, "value")
                val valuePath = "$parent.value"
                definitions[valuePath] = definition.valueDefinition
                collectDefinitionPaths(definition.valueDefinition, valuePath)
            }
            else -> Unit
        }
    }
    collectModelPaths(model, "")
    val allPaths = definitions.keys.sorted()
    val childMap = children.mapValues { it.value.sorted() }
    return FilterPathInfo(paths = allPaths, children = childMap, definitions = definitions)
}
