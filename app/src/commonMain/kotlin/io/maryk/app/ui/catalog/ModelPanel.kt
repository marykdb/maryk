package io.maryk.app.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.maryk.app.state.BrowserState
import io.maryk.app.state.BrowserUiState
import io.maryk.app.state.ModelFieldRef
import io.maryk.app.ui.handPointer
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference

private data class ModelNode(
    val path: String,
    val label: String,
    val type: String,
    val wrapper: IsDefinitionWrapper<*, *, *, *>?,
    val subDefinition: IsPropertyDefinition<*>? = null,
    val children: List<ModelNode> = emptyList(),
    val defaultExpanded: Boolean = true,
)

@Composable
private fun ModelKeyIndexSection(
    dataModel: IsRootDataModel,
    onSelect: (String) -> Unit,
) {
    val indexes = dataModel.Meta.indexes.orEmpty()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IndexDefinitionRow(
                label = "Key",
                indexable = dataModel.Meta.keyDefinition,
                tone = MaterialTheme.colorScheme.tertiary,
                onSelect = onSelect,
            )
            if (indexes.isNotEmpty()) {
                indexes.forEachIndexed { index, indexable ->
                    IndexDefinitionRow(
                        label = "Index ${index + 1}",
                        indexable = indexable,
                        tone = MaterialTheme.colorScheme.primary,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexDefinitionRow(
    label: String,
    indexable: IsIndexable,
    tone: Color,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IndexableChips(indexable = indexable, tone = tone, onSelect = onSelect)
    }
}

@Composable
private fun IndexableChips(
    indexable: IsIndexable,
    tone: Color,
    onSelect: (String) -> Unit,
) {
    WrapRow(
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) {
        when (indexable) {
            is UUIDv4Key -> Chip(label = "UUIDv4 key", tone = tone)
            is UUIDv7Key -> Chip(label = "UUIDv7 key", tone = tone)
            is Multiple -> ChipGroupBox(tone = tone) {
                indexable.references.forEach { nested ->
                    when (nested) {
                        is UUIDv4Key -> Chip(label = "UUIDv4 key", tone = tone)
                        is UUIDv7Key -> Chip(label = "UUIDv7 key", tone = tone)
                        is Multiple -> IndexableChips(indexable = nested, tone = tone, onSelect = onSelect)
                        is Reversed<*> -> ChipContainer(label = "Reversed", tone = tone) {
                            ReferenceChip(reference = nested.reference, tone = tone, onSelect = onSelect)
                        }
                        is ReferenceToMax<*> -> ChipContainer(label = "Ref to Max", tone = tone) {
                            ReferenceChip(reference = nested.reference, tone = tone, onSelect = onSelect)
                        }
                        is IsIndexablePropertyReference<*> -> ReferenceChip(reference = nested, tone = tone, onSelect = onSelect)
                        else -> Chip(label = nested::class.simpleName.orEmpty(), tone = tone)
                    }
                }
            }
            is Reversed<*> -> ChipContainer(label = "Reversed", tone = tone) {
                ReferenceChip(reference = indexable.reference, tone = tone, onSelect = onSelect)
            }
            is ReferenceToMax<*> -> ChipContainer(label = "Ref to Max", tone = tone) {
                ReferenceChip(reference = indexable.reference, tone = tone, onSelect = onSelect)
            }
            is IsIndexablePropertyReference<*> -> ReferenceChip(reference = indexable, tone = tone, onSelect = onSelect)
            else -> Chip(label = indexable::class.simpleName.orEmpty(), tone = tone)
        }
    }
}

@Composable
private fun ReferenceChip(
    reference: IsIndexablePropertyReference<*>,
    tone: Color,
    onSelect: (String) -> Unit,
) {
    val path = referenceLabel(reference)
    Chip(
        label = path,
        tone = tone,
        monospace = true,
        onClick = { onSelect(path) },
    )
}

private fun referenceLabel(reference: IsIndexablePropertyReference<*>): String {
    return (reference as? AnyPropertyReference)?.completeName ?: reference.toString()
}

@Composable
private fun ChipContainer(
    label: String,
    tone: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        color = tone.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tone)
            WrapRow(
                horizontalSpacing = 6.dp,
                verticalSpacing = 4.dp,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ChipGroupBox(
    tone: Color,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isTertiary = tone == colors.tertiary
    Surface(
        color = tone.copy(alpha = if (isTertiary) 0.14f else 0.08f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, tone.copy(alpha = if (isTertiary) 0.4f else 0.3f)),
    ) {
        WrapRow(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalSpacing = 6.dp,
            verticalSpacing = 4.dp,
        ) {
            content()
        }
    }
}
@Composable
private fun Chip(
    label: String,
    tone: Color,
    monospace: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val isTertiary = tone == colors.tertiary
    Surface(
        color = tone.copy(alpha = if (isTertiary) 0.2f else 0.14f),
        shape = MaterialTheme.shapes.small,
        modifier = if (onClick != null) Modifier.handPointer().clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
            ),
            color = tone,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WrapRow(
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hSpace = horizontalSpacing.roundToPx()
        val vSpace = verticalSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints) }
        val maxWidth = if (constraints.maxWidth == Constraints.Infinity) Int.MAX_VALUE else constraints.maxWidth
        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var layoutWidth = 0
        for (placeable in placeables) {
            val nextX = if (x == 0) placeable.width else x + hSpace + placeable.width
            if (nextX > maxWidth && x != 0) {
                layoutWidth = maxOf(layoutWidth, x)
                x = 0
                y += rowHeight + vSpace
                rowHeight = 0
            }
            val placeX = if (x == 0) 0 else x + hSpace
            positions.add(IntOffset(placeX, y))
            x = placeX + placeable.width
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        layoutWidth = maxOf(layoutWidth, x)
        val layoutHeight = y + rowHeight
        val finalWidth = layoutWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val finalHeight = layoutHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(finalWidth, finalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val position = positions[index]
                placeable.placeRelative(position.x, position.y)
            }
        }
    }
}

private fun buildModelRefMap(model: IsRootDataModel): Map<String, ModelFieldRef> {
    val nodes = buildModelNodes(model)
    val map = mutableMapOf<String, ModelFieldRef>()
    fun visit(node: ModelNode) {
        node.wrapper?.let { map[node.path] = ModelFieldRef(node.path, it, definition = it.definition) }
        node.subDefinition?.let { map[node.path] = ModelFieldRef(node.path, wrapper = null, definition = it) }
        node.children.forEach(::visit)
    }
    nodes.forEach(::visit)
    return map
}

@Composable
fun ModelTabPanel(
    state: BrowserState,
    uiState: BrowserUiState,
    modifier: Modifier = Modifier,
) {
    val dataModel = state.currentDataModel()
    if (dataModel == null) {
        Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
            Text("No model selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val nodes = remember(dataModel) { buildModelNodes(dataModel) }
    val refMap = remember(dataModel) { buildModelRefMap(dataModel) }
    val modelId = state.selectedModelId
    LaunchedEffect(dataModel, modelId) {
        val autoPins = collectAutoPinPaths(dataModel)
        uiState.ensureAutoPins(modelId, autoPins)
    }
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModelKeyIndexSection(
            dataModel = dataModel,
            onSelect = { path -> refMap[path]?.let { state.selectModelField(it) } },
        )
        nodes.forEach { node ->
            ModelNodeView(
                node = node,
                uiState = uiState,
                modelId = modelId,
                onSelect = { ref -> state.selectModelField(ref) },
                selectedPath = state.selectedModelField?.path,
            )
        }
    }
}

@Composable
fun ModelDetailsPanel(
    state: BrowserState,
    uiState: BrowserUiState,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedModelField
    Column(
        modifier = modifier.fillMaxHeight().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected == null) {
            Text("Select a field to see details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val details = remember(selected) { buildModelDetails(selected) }
        val definition = selected.wrapper?.definition ?: selected.definition
        details.forEach { detail ->
            DetailRow(detail.first, detail.second)
        }
        if (definition is IsMultiTypeDefinition<*, *, *>) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Types", style = MaterialTheme.typography.labelMedium)
            val cases = definition.typeEnum.cases()
            cases.forEach { typeCase ->
                val selectedType = selected.typeIndex == typeCase.index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .handPointer().clickable { state.selectModelField(selected.copy(typeIndex = typeCase.index)) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        typeCase.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (selectedType) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedType) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    if (selectedType) {
                        Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
fun ModelRawPanel(
    state: BrowserState,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedModelField
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier.fillMaxHeight().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Raw", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    selected?.let { clipboard.setText(AnnotatedString(buildModelRaw(it))) }
                },
                modifier = Modifier.size(20.dp).alpha(0.65f).handPointer(),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy raw model",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (selected == null) {
            Text("Select a field to see raw definition.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val rawText = remember(selected) { buildModelRaw(selected) }
        val scrollState = rememberScrollState()
        Surface(
            color = Color.Transparent,
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                        Text(
                            rawText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ModelNodeView(
    node: ModelNode,
    indent: Int = 0,
    uiState: BrowserUiState,
    modelId: UInt?,
    onSelect: (ModelFieldRef) -> Unit,
    selectedPath: String?,
) {
    if (node.children.isEmpty()) {
        ModelLeafRow(node, indent, uiState, modelId, onSelect, selected = node.path == selectedPath)
        return
    }
    var expanded by remember(node.path) { mutableStateOf(node.defaultExpanded) }
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (indent * 12).dp)
                .handPointer().clickable {
                    node.wrapper?.let { onSelect(ModelFieldRef(node.path, it, definition = it.definition)) }
                    node.subDefinition?.let { onSelect(ModelFieldRef(node.path, wrapper = null, definition = it)) }
                    expanded = !expanded
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(node.label, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                val definition = node.wrapper?.definition
                val pinnable = definition?.let(::isPinnableDefinition) == true && modelId != null
                if (pinnable) {
                    val pinned = uiState.isPinned(modelId, node.path)
                    IconButton(
                        onClick = { uiState.togglePinned(modelId, node.path) },
                        modifier = Modifier.size(20.dp).handPointer(),
                    ) {
                        Icon(
                            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pinned) "Unpin" else "Pin",
                            modifier = Modifier.size(12.dp),
                            tint = if (pinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                    }
                }
                Text(node.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.width(16.dp),
                )
        }
    }
    if (expanded) {
        node.children.forEach { child ->
            ModelNodeView(child, indent + 1, uiState, modelId, onSelect, selectedPath)
        }
    }
}

@Composable
private fun ModelLeafRow(
    node: ModelNode,
    indent: Int,
    uiState: BrowserUiState,
    modelId: UInt?,
    onSelect: (ModelFieldRef) -> Unit,
    selected: Boolean = false,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 12).dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handPointer().clickable {
                    node.wrapper?.let { onSelect(ModelFieldRef(node.path, it, definition = it.definition)) }
                    node.subDefinition?.let { onSelect(ModelFieldRef(node.path, wrapper = null, definition = it)) }
                }
                .padding(vertical = 3.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                node.label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
            Spacer(modifier = Modifier.weight(1f))
            val definition = node.wrapper?.definition
            val pinnable = definition?.let(::isPinnableDefinition) == true && modelId != null
            if (pinnable) {
                val pinned = uiState.isPinned(modelId, node.path)
                IconButton(
                    onClick = { uiState.togglePinned(modelId, node.path) },
                    modifier = Modifier.size(20.dp).handPointer(),
                ) {
                    Icon(
                        if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (pinned) "Unpin" else "Pin",
                        modifier = Modifier.size(12.dp),
                        tint = if (pinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
            }
            Text(node.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isPinnableDefinition(definition: IsPropertyDefinition<*>): Boolean {
    return when (definition) {
        is IsListDefinition<*, *> -> false
        is IsSetDefinition<*, *> -> false
        is IsMapDefinition<*, *, *> -> false
        else -> true
    }
}

private fun collectAutoPinPaths(
    model: IsTypedDataModel<*>,
    prefix: String = "",
): Set<String> {
    val result = mutableSetOf<String>()
    model.forEach { wrapper ->
        val label = wrapper.name
        val path = if (prefix.isBlank()) label else "$prefix.$label"
        val definition = wrapper.definition
        if (definition is IsEmbeddedDefinition<*>) {
            result += collectAutoPinPaths(definition.dataModel, path)
            return@forEach
        }
        if (definition is ValueObjectDefinition<*, *>) {
            result += collectAutoPinPaths(definition.dataModel, path)
            return@forEach
        }
        if (!isPinnableDefinition(definition)) return@forEach
        if (matchesAutoPin(label)) {
            result.add(path)
        }
    }
    return result
}

private fun matchesAutoPin(label: String): Boolean {
    val lower = label.lowercase()
    return lower == "name"
        || lower == "title"
        || lower.startsWith("firstname")
        || lower == "lastname"
        || lower == "surname"
}

private fun buildModelNodes(model: IsTypedDataModel<*>): List<ModelNode> {
    return buildModelNodes(model, "")
}

private fun buildModelNodes(
    model: IsTypedDataModel<*>,
    prefix: String,
): List<ModelNode> {
    return model.map { wrapper ->
        buildModelNode(wrapper, wrapper.name, prefix)
    }
}

private fun buildModelNode(
    wrapper: IsDefinitionWrapper<*, *, *, *>,
    label: String,
    prefix: String,
): ModelNode {
    val path = if (prefix.isBlank()) label else "$prefix.$label"
    val type = definitionTypeHint(wrapper.definition)
    val children = buildDefinitionChildren(wrapper.definition, path)
    val defaultExpanded = children.isEmpty()
    return ModelNode(
        path = path,
        label = label,
        type = type,
        wrapper = wrapper,
        children = children,
        defaultExpanded = defaultExpanded,
    )
}

private fun buildDefinitionChildren(
    definition: IsPropertyDefinition<*>,
    parentPath: String,
): List<ModelNode> {
    return when (definition) {
        is IsEmbeddedDefinition<*> -> buildModelNodes(definition.dataModel, parentPath)
        is IsMultiTypeDefinition<*, *, *> -> buildMultiTypeChildren(definition, parentPath)
        is IsMapDefinition<*, *, *> -> {
            listOf(
                buildDefinitionNode("Key", definition.keyDefinition, "$parentPath.key"),
                buildDefinitionNode("Value", definition.valueDefinition, "$parentPath.value"),
            )
        }
        is IsListDefinition<*, *> -> listOf(
            buildDefinitionNode("Value", definition.valueDefinition, "$parentPath.value"),
        )
        is IsSetDefinition<*, *> -> listOf(
            buildDefinitionNode("Value", definition.valueDefinition, "$parentPath.value"),
        )
        else -> emptyList()
    }
}

private fun buildDefinitionNode(
    label: String,
    definition: IsPropertyDefinition<*>,
    path: String,
): ModelNode {
    val children = buildDefinitionChildren(definition, path)
    return ModelNode(
        path = path,
        label = label,
        type = definitionTypeHint(definition),
        wrapper = null,
        subDefinition = definition,
        children = children,
        defaultExpanded = children.isEmpty(),
    )
}

private fun buildMultiTypeChildren(
    definition: IsMultiTypeDefinition<*, *, *>,
    parentPath: String,
): List<ModelNode> {
    return definition.typeEnum.cases().mapNotNull { typeCase ->
        val sub = definition.definition(typeCase.index) ?: return@mapNotNull null
        val label = typeCase.toString()
        val path = "$parentPath.${typeCase.index}"
        val children = buildDefinitionChildren(sub, path)
        ModelNode(
            path = path,
            label = label,
            type = definitionTypeHint(sub),
            wrapper = null,
            subDefinition = sub,
            children = children,
            defaultExpanded = children.isEmpty(),
        )
    }
}

private fun buildModelDetails(ref: ModelFieldRef): List<Pair<String, String>> {
    val definition = resolveDefinition(ref)
    val details = mutableListOf<Pair<String, String>>()
    details += "Field" to ref.path
    details += "Type" to definitionTypeHint(definition)
    val definitionName = definition::class.simpleName?.removeSuffix("Definition") ?: "Definition"
    details += "Definition" to definitionName
    details += "Required" to definition.required.toString()
    details += "Final" to definition.final.toString()
    if (definition is HasDefaultValueDefinition<*>) {
        details += "Default" to (definition.default?.toString() ?: "—")
    }
    if (definition is IsComparableDefinition<*, *>) {
        details += "Unique" to definition.unique.toString()
        details += "Min" to (definition.minValue?.toString() ?: "—")
        details += "Max" to (definition.maxValue?.toString() ?: "—")
    }
    if (definition is StringDefinition) {
        details += "Min size" to (definition.minSize?.toString() ?: "—")
        details += "Max size" to (definition.maxSize?.toString() ?: "—")
        details += "Regex" to (definition.regEx ?: "—")
        details += "Unique" to definition.unique.toString()
    }
    if (definition is NumberDefinition<*>) {
        details += "Number type" to definition.type.type.name
        details += "Reversed" to (definition.reversedStorage?.toString() ?: "—")
    }
    if (definition is IsListDefinition<*, *>) {
        details += "Min size" to (definition.minSize?.toString() ?: "—")
        details += "Max size" to (definition.maxSize?.toString() ?: "—")
        details += "Value type" to definitionTypeHint(definition.valueDefinition)
    }
    if (definition is IsSetDefinition<*, *>) {
        details += "Min size" to (definition.minSize?.toString() ?: "—")
        details += "Max size" to (definition.maxSize?.toString() ?: "—")
        details += "Value type" to definitionTypeHint(definition.valueDefinition)
    }
    if (definition is IsMapDefinition<*, *, *>) {
        details += "Min size" to (definition.minSize?.toString() ?: "—")
        details += "Max size" to (definition.maxSize?.toString() ?: "—")
        details += "Key type" to definitionTypeHint(definition.keyDefinition)
        details += "Value type" to definitionTypeHint(definition.valueDefinition)
    }
    if (definition is IsReferenceDefinition<*, *>) {
        details += "Target model" to definition.dataModel.Meta.name
    }
    if (definition is IsEmbeddedDefinition<*>) {
        val modelName = when (val model = definition.dataModel) {
            is IsRootDataModel -> model.Meta.name
            is IsValuesDataModel -> model.Meta.name
            else -> model::class.simpleName.orEmpty()
        }
        details += "Embedded model" to modelName
    }
    if (definition is EnumDefinition<*>) {
        val cases = definition.enum.cases().joinToString(", ") { it.toString() }
        details += "Options" to cases
    }
    val baseDefinition = ref.wrapper?.definition ?: ref.definition
    if (baseDefinition is IsMultiTypeDefinition<*, *, *>) {
        details += "Types" to baseDefinition.typeEnum.cases().joinToString(", ") { it.toString() }
        if (ref.typeIndex != null) {
            details += "Selected type" to ref.typeIndex.toString()
        }
    }
    return details
}

private fun buildModelRaw(ref: ModelFieldRef): String {
    val definition = resolveDefinition(ref)
    val details = buildModelDetails(ref)
    val builder = StringBuilder()
    details.forEach { (label, value) ->
        builder.append(label).append(": ").append(value).append('\n')
    }
    if (definition is EnumDefinition<*>) {
        builder.append("Cases:\n")
        definition.enum.cases().forEach { case ->
            builder.append("  - ").append(case.toString()).append('\n')
        }
    }
    return builder.toString().trimEnd()
}

private fun resolveDefinition(ref: ModelFieldRef): IsPropertyDefinition<*> {
    val definition = ref.wrapper?.definition ?: ref.definition ?: return StringDefinition()
    if (definition is IsMultiTypeDefinition<*, *, *>) {
        val typeIndex = ref.typeIndex
        if (typeIndex != null) {
            return definition.definition(typeIndex) ?: definition
        }
    }
    return definition
}

private fun definitionTypeHint(definition: IsPropertyDefinition<*>): String {
    return when (definition) {
        is NumberDefinition<*> -> "Number (${definition.type.type.name})"
        else -> definition::class.simpleName?.removeSuffix("Definition").orEmpty()
    }
}
