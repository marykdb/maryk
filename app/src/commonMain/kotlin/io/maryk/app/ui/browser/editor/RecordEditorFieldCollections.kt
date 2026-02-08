package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.maryk.app.state.BrowserState
import io.maryk.app.ui.handPointer
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition

internal fun mapContentStartIndent(
    indent: Int,
    isIncMap: Boolean,
): Int = indent * 12 + if (isIncMap) 140 else 148

@Composable
internal fun EditorListField(
    label: String,
    path: String,
    definition: IsListDefinition<Any, *>,
    value: List<Any>?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    onValueChange: (List<Any>?) -> Unit,
    onError: (String?) -> Unit,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val listValue = value ?: emptyList()
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = "List",
        countLabel = listValue.size.toString(),
        enabled = enabled,
        allowUnset = !required && enabled,
        onUnset = { onValueChange(null) },
        onToggle = { expanded = !expanded },
        onAdd = if (enabled) {
            {
                expanded = true
                val newItem = defaultSetItem(definition.valueDefinition, emptySet()) ?: return@EditorCollapsibleHeader
                onValueChange(listValue + newItem)
                onError(validateValue(definition, listValue + newItem, required))
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    if (expanded) {
        listValue.forEachIndexed { index, item ->
            EditorInlineValue(
                label = "Item ${index + 1}",
                path = "$path[$index]",
                definition = definition.valueDefinition,
                value = item,
                enabled = enabled,
                required = true,
                indent = indent + 1,
                state = state,
                parentRef = null,
                editorState = null,
                onValueChange = { updated ->
                    val next = listValue.toMutableList()
                    if (updated == null) return@EditorInlineValue
                    next[index] = updated
                    onValueChange(next)
                    onError(validateValue(definition, next, required))
                },
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = (indent * 12 + 148).dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            if (index <= 0) return@IconButton
                            val next = listValue.toMutableList()
                            val moveItem = next.removeAt(index)
                            next.add(index - 1, moveItem)
                            onValueChange(next)
                            onError(validateValue(definition, next, required))
                        },
                        modifier = Modifier.size(22.dp).handPointer(),
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = {
                            if (index >= listValue.lastIndex) return@IconButton
                            val next = listValue.toMutableList()
                            val moveItem = next.removeAt(index)
                            next.add(index + 1, moveItem)
                            onValueChange(next)
                            onError(validateValue(definition, next, required))
                        },
                        modifier = Modifier.size(22.dp).handPointer(),
                        enabled = index < listValue.lastIndex,
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = {
                            val next = listValue.toMutableList()
                            next.removeAt(index)
                            onValueChange(next)
                            onError(validateValue(definition, next, required))
                        },
                        modifier = Modifier.size(22.dp).handPointer(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorSetField(
    label: String,
    path: String,
    definition: IsSetDefinition<Any, *>,
    value: Set<Any>?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    onValueChange: (Set<Any>?) -> Unit,
    onError: (String?) -> Unit,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val setValue = value ?: emptySet()
    val listValue = setValue.toList()
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = "Set",
        countLabel = setValue.size.toString(),
        enabled = enabled,
        allowUnset = !required && enabled,
        onUnset = { onValueChange(null) },
        onToggle = { expanded = !expanded },
        onAdd = if (enabled) {
            {
                expanded = true
                val newItem = defaultSetItem(definition.valueDefinition, setValue)
                if (newItem == null) {
                    onPathError(path, "No available unique values for this set.")
                    return@EditorCollapsibleHeader
                }
                onPathError(path, null)
                val next = setValue + newItem
                onValueChange(next)
                onError(validateValue(definition, next, required))
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    if (expanded) {
        listValue.forEachIndexed { index, item ->
            EditorInlineValue(
                label = "Item ${index + 1}",
                path = "$path[$index]",
                definition = definition.valueDefinition,
                value = item,
                enabled = enabled,
                required = true,
                indent = indent + 1,
                state = state,
                parentRef = null,
                editorState = null,
                onValueChange = { updated ->
                    if (updated == null) return@EditorInlineValue
                    if (updated != item && setValue.contains(updated)) {
                        onPathError("$path[$index]", "Item already exists.")
                        return@EditorInlineValue
                    }
                    onPathError("$path[$index]", null)
                    val next = setValue.toMutableSet()
                    next.remove(item)
                    next.add(updated)
                    onValueChange(next)
                    onError(validateValue(definition, next, required))
                },
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = (indent * 12 + 148).dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            val next = setValue.toMutableSet()
                            next.remove(item)
                            onValueChange(next)
                            onError(validateValue(definition, next, required))
                        },
                        modifier = Modifier.size(22.dp).handPointer(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorMapField(
    label: String,
    path: String,
    definition: IsMapDefinition<Any, Any, *>,
    value: Map<Any, Any>?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    pendingAdds: List<Any>,
    onAddPending: ((Any) -> Unit)?,
    onUpdatePending: ((Int, Any) -> Unit)?,
    onRemovePending: ((Int) -> Unit)?,
    focusPath: String?,
    onConsumeFocus: (() -> Unit)?,
    onValueChange: (Map<Any, Any>?) -> Unit,
    onError: (String?) -> Unit,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val mapValue = value ?: emptyMap()
    val entries = mapValue.entries.toList()
    val isIncMap = definition is IncrementingMapDefinition<*, *, *>
    val contentStart = mapContentStartIndent(indent, isIncMap).dp
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = if (isIncMap) "IncMap" else "Map",
        countLabel = (mapValue.size + pendingAdds.size).toString(),
        enabled = enabled,
        allowUnset = !required && enabled,
        onUnset = { onValueChange(null) },
        onToggle = { expanded = !expanded },
        onAdd = if (enabled) {
            {
                expanded = true
                val newValue = defaultSetItem(definition.valueDefinition, emptySet()) ?: return@EditorCollapsibleHeader
                if (isIncMap && onAddPending != null) {
                    onPathError(path, null)
                    onAddPending(newValue)
                } else {
                    val key = defaultMapKey(definition.keyDefinition, mapValue.keys)
                    if (key == null) {
                        onPathError(path, "No available key values for this map.")
                        return@EditorCollapsibleHeader
                    }
                    onPathError(path, null)
                    val next = mapValue.toMutableMap()
                    next[key] = newValue
                    onValueChange(next)
                    onError(validateValue(definition, next, required))
                }
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = contentStart))
    }
    if (expanded) {
        if (isIncMap && pendingAdds.isNotEmpty()) {
            pendingAdds.forEachIndexed { index, pending ->
                val entryPath = "$path.^$index"
                val autoFocus = focusPath == path && index == 0
                if (autoFocus) {
                    LaunchedEffect(entryPath) { onConsumeFocus?.invoke() }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = contentStart),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EditorInlineValue(
                        label = "New ${index + 1}",
                        path = "$entryPath.value",
                        definition = definition.valueDefinition as IsPropertyDefinition<*>,
                        value = pending,
                        enabled = enabled,
                        required = true,
                        indent = indent + 1,
                        state = state,
                        parentRef = null,
                        editorState = null,
                        autoFocus = autoFocus,
                        onValueChange = { updated ->
                            if (updated == null || onUpdatePending == null) return@EditorInlineValue
                            onUpdatePending(index, updated)
                        },
                        onError = onError,
                        errorProvider = errorProvider,
                        onPathError = onPathError,
                    )
                    if (enabled && onRemovePending != null) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = { onRemovePending(index) },
                                modifier = Modifier.size(22.dp).handPointer(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove new entry", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        entries.forEachIndexed { index, entry ->
            val entryPath = "$path[${definition.keyDefinition.asString(entry.key)}]"
            var keyText by remember(entryPath) { mutableStateOf(definition.keyDefinition.asString(entry.key)) }
            var keyError by remember(entryPath) { mutableStateOf<String?>(null) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = contentStart),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isIncMap) {
                    EditorTextRow(
                        label = "Key ${index + 1}",
                        value = keyText,
                        onValueChange = { },
                        enabled = false,
                        error = null,
                    )
                } else {
                    EditorTextRow(
                        label = "Key ${index + 1}",
                        value = keyText,
                        onValueChange = { next ->
                            keyText = next
                            val parsed = parseSimpleValue(definition.keyDefinition, next)
                            if (parsed is ParseResult.Error) {
                                keyError = parsed.message
                                onPathError("$entryPath.key", keyError)
                                return@EditorTextRow
                            }
                            if (parsed is ParseResult.Value) {
                                val validationError = validateValue(definition.keyDefinition, parsed.value, required = true)
                                if (validationError != null) {
                                    keyError = validationError
                                    onPathError("$entryPath.key", keyError)
                                    return@EditorTextRow
                                }
                                keyError = null
                                onPathError("$entryPath.key", null)
                                if (parsed.value != entry.key && mapValue.containsKey(parsed.value)) {
                                    keyError = "Key already exists."
                                    onPathError("$entryPath.key", keyError)
                                    return@EditorTextRow
                                }
                                val nextMap = mapValue.toMutableMap()
                                nextMap.remove(entry.key)
                                nextMap[parsed.value] = entry.value
                                onValueChange(nextMap)
                                onError(validateValue(definition, nextMap, required))
                            }
                        },
                        enabled = enabled,
                        error = keyError,
                    )
                }
                EditorInlineValue(
                    label = "Value ${index + 1}",
                    path = "$entryPath.value",
                    definition = definition.valueDefinition,
                    value = entry.value,
                    enabled = enabled,
                    required = true,
                    indent = indent + 1,
                    state = state,
                    parentRef = null,
                    editorState = null,
                    onValueChange = { updated ->
                        if (updated == null) return@EditorInlineValue
                        val next = mapValue.toMutableMap()
                        next[entry.key] = updated
                        onValueChange(next)
                        onError(validateValue(definition, next, required))
                    },
                    onError = onError,
                    errorProvider = errorProvider,
                    onPathError = onPathError,
                )
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = {
                                val next = mapValue.toMutableMap()
                                next.remove(entry.key)
                                onValueChange(next)
                                onError(validateValue(definition, next, required))
                            },
                            modifier = Modifier.size(22.dp).handPointer(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove entry", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
