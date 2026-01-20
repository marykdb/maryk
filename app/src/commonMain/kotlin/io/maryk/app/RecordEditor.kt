package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
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
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
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
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.properties.types.invoke
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.query.requests.scan
import maryk.core.query.requests.get

internal enum class RecordEditorMode {
    ADD,
    EDIT,
}

@Composable
internal fun RecordEditorDialog(
    state: BrowserState,
    mode: RecordEditorMode,
    dataModel: IsRootDataModel,
    initialValues: Values<IsRootDataModel>,
    initialKeyText: String?,
    onDismiss: () -> Unit,
) {
    val editorState = remember(initialValues, dataModel, mode) {
        RecordEditState(dataModel, initialValues, allowFinalEdit = mode == RecordEditorMode.ADD)
    }
    var keyText by remember(initialKeyText, mode) { mutableStateOf(initialKeyText.orEmpty()) }
    var keyError by remember(mode, initialKeyText) { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var awaitingSave by remember { mutableStateOf(false) }
    val lastMessage = state.lastActionMessage
    val canEdit = !state.isWorking
    val hasErrors = editorState.hasErrors || keyError != null
    val hasChanges = mode == RecordEditorMode.ADD || editorState.hasChanges

    if (awaitingSave && lastMessage != null) {
        if (lastMessage.contains("failed", ignoreCase = true)) {
            saveError = lastMessage
            awaitingSave = false
        } else {
            awaitingSave = false
            onDismiss()
        }
    }

    ModalSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (mode == RecordEditorMode.ADD) "Add record" else "Edit record",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        dataModel.Meta.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close editor", modifier = Modifier.size(16.dp))
                }
            }

            if (mode == RecordEditorMode.ADD) {
                EditorTextRow(
                    label = "Key (optional)",
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        keyError = null
                    },
                    placeholder = "Leave empty for auto key",
                    enabled = true,
                    error = keyError,
                )
            } else if (!initialKeyText.isNullOrBlank()) {
                EditorTextRow(
                    label = "Key",
                    value = initialKeyText,
                    onValueChange = {},
                    enabled = false,
                )
            }

            saveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            val scrollState = rememberScrollState()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dataModel.forEach { wrapper ->
                            EditorField(
                                editorState = editorState,
                                state = state,
                                label = wrapper.name,
                                wrapper = wrapper,
                                parentRef = null,
                                indent = 0,
                                errorProvider = editorState::errorFor,
                                onError = editorState::setError,
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = {
                        if (!editorState.validateAll()) {
                            saveError = "Fix validation errors before saving."
                            return@Button
                        }
                        if (mode == RecordEditorMode.ADD) {
                            val key = keyText.trim().takeIf { it.isNotBlank() }?.let {
                                runCatching { dataModel.key(it) }.getOrNull()
                            }
                            if (keyText.isNotBlank() && key == null) {
                                keyError = "Invalid key format."
                                return@Button
                            }
                            awaitingSave = true
                            state.addRecord(editorState.serializableValues(), key)
                        } else {
                            if (!editorState.hasChanges) {
                                saveError = "No changes to save."
                                return@Button
                            }
                            val changes = editorState.buildChanges()
                            if (changes.isEmpty()) {
                                saveError = "No changes to save."
                                return@Button
                            }
                            awaitingSave = true
                            state.applyRecordChanges(changes)
                        }
                    },
                    enabled = canEdit && hasChanges && !hasErrors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private class RecordEditState(
    val dataModel: IsRootDataModel,
    initialValues: Values<IsRootDataModel>,
    val allowFinalEdit: Boolean,
) {
    val originalValues: Values<IsRootDataModel> = initialValues
    var currentValues by mutableStateOf(initialValues)
    private val errors = mutableStateMapOf<String, String>()
    private val pendingIncMapAdds = mutableStateMapOf<String, MutableList<Any>>()
    private val pendingIncMapRefs = mutableStateMapOf<String, IncMapReference<Comparable<Any>, Any, *>>()
    var pendingIncMapFocusPath by mutableStateOf<String?>(null)
    private var showValidationErrors by mutableStateOf(false)

    val hasErrors: Boolean
        get() = errors.isNotEmpty()

    val hasChanges: Boolean
        get() = currentValues != originalValues || pendingIncMapAdds.isNotEmpty()

    fun errorFor(path: String): String? = errors[path]

    fun setError(path: String, message: String?) {
        if (message == null) {
            errors.remove(path)
        } else if (showValidationErrors || message == "Invalid format." || message == "Invalid key.") {
            errors[path] = message
        }
    }

    fun updateValue(
        ref: AnyPropertyReference,
        value: Any?,
    ) {
        val index = propertyIndex(ref)
        currentValues = copyWithValue(currentValues, index, value)
    }

    fun pendingIncMapValues(path: String): List<Any> {
        return pendingIncMapAdds[path] ?: emptyList()
    }

    fun addIncMapValue(path: String, ref: IncMapReference<Comparable<Any>, Any, *>, value: Any) {
        val list = pendingIncMapAdds.getOrPut(path) { mutableStateListOf() }
        list.add(0, value)
        pendingIncMapRefs[path] = ref
        pendingIncMapFocusPath = path
    }

    fun removeIncMapValue(path: String, index: Int) {
        val list = pendingIncMapAdds[path] ?: return
        if (index in list.indices) {
            list.removeAt(index)
        }
        if (list.isEmpty()) {
            pendingIncMapAdds.remove(path)
            pendingIncMapRefs.remove(path)
        }
    }

    fun consumeIncMapFocus(path: String) {
        if (pendingIncMapFocusPath == path) {
            pendingIncMapFocusPath = null
        }
    }

    fun validateAll(): Boolean {
        errors.clear()
        showValidationErrors = true
        dataModel.forEach { wrapper ->
            val ref = wrapper.ref(null)
            val value = resolvePropertyValue(ref, currentValues)
            validateField(wrapper.name, wrapper.definition, ref, value)
        }
        return errors.isEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateField(
        path: String,
        definition: IsPropertyDefinition<*>,
        ref: AnyPropertyReference,
        value: Any?,
    ) {
        val error = try {
            (definition as IsPropertyDefinition<Any>).validateWithRef(null, value) {
                ref as? IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
            }
            null
        } catch (e: Throwable) {
            formatValidationMessage(e)
        }
        if (error != null) {
            errors[path] = error
        }
        when (definition) {
            is IsEmbeddedValuesDefinition<*, *> -> {
                val embedded = value as? Values<IsValuesDataModel> ?: return
                definition.dataModel.forEach { child ->
                    val childRef = child.ref(null)
                    val childPath = "$path.${child.name}"
                    validateField(childPath, child.definition, childRef, resolvePropertyValue(childRef, embedded))
                }
            }
            is IsCollectionDefinition<*, *, *, *> -> {
                val items = value as? Collection<Any> ?: return
                items.forEachIndexed { index, item ->
                    val itemPath = "$path[$index]"
                    validateItem(itemPath, definition.valueDefinition, item)
                }
            }
            is IsMapDefinition<*, *, *> -> {
                val map = value as? Map<Any, Any> ?: return
                map.entries.forEach { (key, itemValue) ->
                    val keyText = (definition.keyDefinition as? IsSimpleValueDefinition<Any, *>)?.asString(key)
                        ?: key.toString()
                    val keyPath = "$path[$keyText]"
                    validateItem("$keyPath.key", definition.keyDefinition, key)
                    validateItem("$keyPath.value", definition.valueDefinition, itemValue)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateItem(path: String, definition: IsSubDefinition<*, *>, value: Any?) {
        val propertyDefinition = definition as? IsPropertyDefinition<*> ?: return
        val error = try {
            (propertyDefinition as IsPropertyDefinition<Any>).validateWithRef(null, value) { null }
            null
        } catch (e: Throwable) {
            formatValidationMessage(e)
        }
        if (error != null) {
            errors[path] = error
        }
    }

    private fun propertyIndex(ref: AnyPropertyReference): UInt {
        @Suppress("UNCHECKED_CAST")
        return (ref as IsPropertyReferenceForValues<Any, Any, *, *>).index
    }

    fun serializableValues(): Values<IsRootDataModel> {
        return sanitizeValuesForWrite(currentValues)
    }

    fun buildChanges(): List<IsChange> {
        val pairs = mutableListOf<IsReferenceValueOrNullPair<Any>>()
        dataModel.forEach { wrapper ->
            val ref = wrapper.ref(null)
            val current = resolvePropertyValue(ref, currentValues)
            val original = resolvePropertyValue(ref, originalValues)
            if (current != original) {
                val sanitized = sanitizeValueForWrite(wrapper.definition, current)
                pairs += buildReferencePair(ref, sanitized)
            }
        }
        val changes = mutableListOf<IsChange>()
        if (pairs.isNotEmpty()) {
            val change = Change.create {
                referenceValuePairs with pairs
            }.toDataObject()
            changes += change
        }
        if (pendingIncMapAdds.isNotEmpty()) {
            pendingIncMapAdds.forEach { (path, values) ->
                if (values.isEmpty()) return@forEach
                val ref = pendingIncMapRefs[path] ?: return@forEach
                val addValues = values.reversed()
                changes += IncMapChange(ref.change(addValues = addValues))
            }
        }
        return changes
    }
}

@Composable
private fun EditorField(
    editorState: RecordEditState,
    state: BrowserState,
    label: String,
    wrapper: IsDefinitionWrapper<*, *, *, *>,
    parentRef: AnyPropertyReference?,
    indent: Int,
    errorProvider: (String) -> String?,
    onError: (String, String?) -> Unit,
) {
    val ref = wrapper.ref(parentRef)
    val definition = wrapper.definition
    val currentValue = resolvePropertyValue(ref, editorState.currentValues)
    val required = definition.required
    val enabled = !definition.final || editorState.allowFinalEdit || currentValue == null
    val path = ref.completeName

    when (definition) {
        is IsListDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorListField(
                label = label,
                path = path,
                definition = definition as IsListDefinition<Any, *>,
                value = currentValue as? List<Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
        is IsSetDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorSetField(
                label = label,
                path = path,
                definition = definition as IsSetDefinition<Any, *>,
                value = currentValue as? Set<Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
        is IsMapDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            val incMapRef = if (definition is IncrementingMapDefinition<*, *, *>) {
                ref as? IncMapReference<Comparable<Any>, Any, *>
            } else null
            @Suppress("UNCHECKED_CAST")
            EditorMapField(
                label = label,
                path = path,
                definition = definition as IsMapDefinition<Any, Any, *>,
                value = currentValue as? Map<Any, Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                pendingAdds = editorState.pendingIncMapValues(path),
                onAddPending = if (incMapRef != null) {
                    { value -> editorState.addIncMapValue(path, incMapRef, value) }
                } else null,
                onRemovePending = if (incMapRef != null) {
                    { index -> editorState.removeIncMapValue(path, index) }
                } else null,
                focusPath = editorState.pendingIncMapFocusPath,
                onConsumeFocus = { editorState.consumeIncMapFocus(path) },
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
        is IsMultiTypeDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorMultiTypeField(
                label = label,
                path = path,
                definition = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>,
                value = currentValue as? TypedValue<TypeEnum<Any>, Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
        is IsEmbeddedValuesDefinition<*, *> -> {
            val defaultExpanded = true
            @Suppress("UNCHECKED_CAST")
            val embeddedValues = currentValue as? Values<IsValuesDataModel>
            if (required && embeddedValues == null) {
                LaunchedEffect(path) {
                    editorState.updateValue(ref, createDefaultValues(definition))
                }
            }
            val canUnset = !required && enabled && embeddedValues != null
            EditorEmbeddedField(
                label = label,
                path = path,
                definition = definition,
                values = embeddedValues,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                defaultExpanded = defaultExpanded,
                error = errorProvider(path),
                canUnset = canUnset,
                onUnset = { editorState.updateValue(ref, null) },
                onValueChange = { editorState.updateValue(ref, it) },
                allowFinalEdit = editorState.allowFinalEdit,
                parentRef = ref,
                editorState = editorState,
                errorProvider = errorProvider,
                onError = onError,
            )
        }
        is IsEmbeddedObjectDefinition<*, *, *, *> -> {
            val defaultExpanded = true
            if (required && currentValue == null) {
                LaunchedEffect(path) {
                    editorState.updateValue(ref, createDefaultObjectValues(definition))
                }
            }
            EditorEmbeddedObjectField(
                label = label,
                path = path,
                definition = definition,
                value = currentValue,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                defaultExpanded = defaultExpanded,
                error = errorProvider(path),
                onValueChange = { editorState.updateValue(ref, it) },
                allowFinalEdit = editorState.allowFinalEdit,
                errorProvider = errorProvider,
                onError = onError,
            )
        }
        is IsReferenceDefinition<*, *> -> {
            ReferenceEditor(
                label = label,
                path = path,
                definition = definition,
                value = currentValue,
                enabled = enabled,
                required = required,
                indent = indent,
                error = errorProvider(path),
                state = state,
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
            )
        }
        else -> {
            EditorValueField(
                label = label,
                path = path,
                definition = definition,
                value = currentValue,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
            )
        }
    }
}

@Composable
private fun EditorValueField(
    label: String,
    path: String,
    definition: IsPropertyDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    when (definition) {
        is EnumDefinition<*> -> {
            EnumEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                onValueChange = {
                    onValueChange(it)
                    onError(validateValue(definition, it, required))
                },
            )
        }
        is BooleanDefinition -> {
            BooleanEditor(
                label = label,
                path = path,
                value = value as? Boolean,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                onValueChange = {
                    onValueChange(it)
                    onError(validateValue(definition, it, required))
                },
            )
        }
        is DateDefinition -> {
            DateEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is TimeDefinition -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = "HH:MM:SS",
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is DateTimeDefinition -> {
            DateTimeEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is ValueObjectDefinition<*, *> -> {
            EditorValueObjectField(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = error,
                onValueChange = onValueChange,
                allowFinalEdit = true,
                errorProvider = { error },
                onError = { _, message -> onError(message) },
            )
        }
        is NumberDefinition<*> -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = "Number",
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        else -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = null,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
    }
}

@Composable
private fun SimpleTextEditor(
    label: String,
    path: String,
    definition: IsPropertyDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    placeholder: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }

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
        placeholder = placeholder,
        error = error,
        autoFocus = autoFocus,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEditor(
    label: String,
    path: String,
    definition: DateDefinition,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }
    val initialMillis = (value as? LocalDate)
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showPicker by remember(path) { mutableStateOf(false) }

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
                val parsedDate = parsed.value as? LocalDate
                if (parsedDate != null) {
                    datePickerState.selectedDateMillis =
                        parsedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                }
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = "YYYY-MM-DD",
        error = error,
        autoFocus = autoFocus,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
        trailingContent = {
            IconButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
            }
        },
    )
    val selectedMillis = datePickerState.selectedDateMillis
    if (selectedMillis != null) {
        LaunchedEffect(selectedMillis) {
            val date = Instant.fromEpochMilliseconds(selectedMillis)
                .toLocalDateTime(TimeZone.UTC)
                .date
            val formatted = formatValue(definition, date)
            if (formatted != text) {
                text = formatted
            }
            onValueChange(date)
            onError(validateValue(definition, date, required))
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeEditor(
    label: String,
    path: String,
    definition: DateTimeDefinition,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }
    val initialDateTime = value as? LocalDateTime
    val initialMillis = initialDateTime
        ?.date
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = initialDateTime?.hour ?: 0,
        initialMinute = initialDateTime?.minute ?: 0,
        is24Hour = true,
    )
    var showPicker by remember(path) { mutableStateOf(false) }

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
                val parsedDateTime = parsed.value as? LocalDateTime
                if (parsedDateTime != null) {
                    datePickerState.selectedDateMillis =
                        parsedDateTime.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                    timePickerState.hour = parsedDateTime.hour
                    timePickerState.minute = parsedDateTime.minute
                }
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = "YYYY-MM-DDTHH:MM:SS",
        error = error,
        autoFocus = autoFocus,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
        trailingContent = {
            IconButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date/time", modifier = Modifier.size(16.dp))
            }
        },
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            Column(
                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DatePicker(state = datePickerState, showModeToggle = false)
                TimePicker(state = timePickerState)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    val selectedMillis = datePickerState.selectedDateMillis
    if (selectedMillis != null) {
        LaunchedEffect(selectedMillis, timePickerState.hour, timePickerState.minute) {
            val date = Instant.fromEpochMilliseconds(selectedMillis)
                .toLocalDateTime(TimeZone.UTC)
                .date
            val time = LocalTime(timePickerState.hour, timePickerState.minute, 0)
            val dateTime = date.atTime(time)
            val formatted = formatValue(definition, dateTime)
            if (formatted != text) {
                text = formatted
            }
            onValueChange(dateTime)
            onError(validateValue(definition, dateTime, required))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ReferenceEditor(
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
        val result = runCatching {
            connection.dataStore.execute(
                dataModel.get(
                    key,
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
            IconButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = "Pick reference", modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = { showInfoDialog = true },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = "Reference info", modifier = Modifier.size(14.dp))
            }
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

@Composable
private fun ReferenceInfoDialog(
    state: BrowserState,
    details: RecordDetails?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(12.dp).widthIn(min = 480.dp, max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference data", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                    }
                }
                when {
                    loading -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    details == null -> Text("Not found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Box(modifier = Modifier.heightIn(min = 320.dp, max = 560.dp)) {
                        InspectorData(state, details, showEdit = false)
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
                Text("Pick reference", style = MaterialTheme.typography.titleSmall)
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
                            modifier = Modifier.height(30.dp).widthIn(min = 140.dp).clickable { sortExpanded = true },
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
                        modifier = Modifier.size(28.dp),
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
                                    .clickable { onPick(row.keyText) }
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
                    TextButton(onClick = onDismiss) { Text("Close") }
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
        is Multiple -> indexable.references
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

@Composable
private fun EnumEditor(
    label: String,
    path: String,
    definition: EnumDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    onValueChange: (Any?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    @Suppress("UNCHECKED_CAST")
    val enumDefinition = definition.enum as IndexedEnumDefinition<IndexedEnumComparable<Any>>
    val cases = enumDefinition.cases()
    val currentLabel = cases.firstOrNull { it == value }?.let { enumDefinition.asString(it) }.orEmpty()
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp).widthIn(min = 120.dp).clickable(enabled = enabled) { expanded = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (currentLabel.isBlank()) "Select…" else currentLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    cases.forEach { case ->
                        DropdownMenuItem(
                            text = { Text(enumDefinition.asString(case), style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expanded = false
                                onValueChange(case)
                            },
                            enabled = enabled,
                        )
                    }
                }
            }
            if (!required && enabled) {
                IconButton(onClick = { onValueChange(null) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BooleanEditor(
    label: String,
    path: String,
    value: Boolean?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    onValueChange: (Boolean?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val labelText = when (value) {
        null -> if (required) "Select…" else "Unset"
        true -> "True"
        false -> "False"
    }
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Box {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (enabled) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.height(30.dp).widthIn(min = 120.dp).clickable(enabled = enabled) { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(labelText, style = MaterialTheme.typography.bodySmall)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("True") }, onClick = { expanded = false; onValueChange(true) }, enabled = enabled)
                DropdownMenuItem(text = { Text("False") }, onClick = { expanded = false; onValueChange(false) }, enabled = enabled)
                if (!required) {
                    DropdownMenuItem(text = { Text("Unset") }, onClick = { expanded = false; onValueChange(null) }, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun EditorEmbeddedField(
    label: String,
    path: String,
    definition: IsEmbeddedValuesDefinition<*, *>,
    values: Values<IsValuesDataModel>?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    defaultExpanded: Boolean,
    error: String?,
    canUnset: Boolean,
    onUnset: () -> Unit,
    onValueChange: (Values<IsValuesDataModel>) -> Unit,
    allowFinalEdit: Boolean,
    parentRef: AnyPropertyReference?,
    editorState: RecordEditState?,
    errorProvider: (String) -> String?,
    onError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(defaultExpanded) }
    val hasValue = values != null
    val typeLabel = definition.dataModel.Meta.name
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = typeLabel,
        countLabel = null,
        enabled = enabled,
        allowUnset = canUnset,
        onUnset = onUnset,
        onToggle = { expanded = !expanded },
        onAdd = if (enabled && !hasValue) {
            {
                expanded = true
                onValueChange(createDefaultValues(definition))
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    if (expanded && values != null) {
        definition.dataModel.forEach { child ->
            val childRef = child.ref(parentRef)
            val childValue = resolvePropertyValue(childRef, values)
            val childPath = "$path.${child.name}"
            EditorInlineValue(
                label = child.name,
                path = childPath,
                definition = child.definition,
                value = childValue,
                enabled = enabled && (!child.definition.final || allowFinalEdit || childValue == null),
                required = child.definition.required,
                indent = indent + 1,
                state = state,
                parentRef = childRef,
                editorState = editorState,
                onValueChange = { updated ->
                    val updatedValues = valuesWithChange(values, childRef, updated)
                    onValueChange(updatedValues)
                },
                onError = { onError(childPath, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
    }
}

@Composable
private fun EditorEmbeddedObjectField(
    label: String,
    path: String,
    definition: IsEmbeddedObjectDefinition<*, *, *, *>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    defaultExpanded: Boolean,
    error: String?,
    onValueChange: (Any?) -> Unit,
    allowFinalEdit: Boolean,
    errorProvider: (String) -> String?,
    onError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(defaultExpanded) }
    @Suppress("UNCHECKED_CAST")
    val dataModel = definition.dataModel as IsTypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>
    val objectValues = objectValuesFromValue(dataModel, value)
    val hasValue = objectValues != null
    val typeLabel = dataModel::class.simpleName ?: "Object"
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = typeLabel,
        countLabel = null,
        enabled = enabled,
        allowUnset = !required && enabled && hasValue,
        onUnset = { onValueChange(null) },
        onToggle = { expanded = !expanded },
        onAdd = if (enabled && !hasValue) {
            {
                expanded = true
                onValueChange(createDefaultObjectValues(definition))
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    if (expanded && objectValues != null) {
        dataModel.forEach { child ->
            val childValue = objectValues.original(child.index)
            val childPath = "$path.${child.name}"
            EditorInlineValue(
                label = child.name,
                path = childPath,
                definition = child.definition,
                value = childValue,
                enabled = enabled && (!child.definition.final || allowFinalEdit || childValue == null),
                required = child.definition.required,
                indent = indent + 1,
                state = state,
                parentRef = null,
                editorState = null,
                onValueChange = { updated ->
                    val nextValues = objectValuesWithChange(dataModel, objectValues, child.index, updated)
                    onValueChange(nextValues)
                },
                onError = { onError(childPath, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
    }
}

@Composable
private fun EditorValueObjectField(
    label: String,
    path: String,
    definition: ValueObjectDefinition<*, *>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    onValueChange: (Any?) -> Unit,
    allowFinalEdit: Boolean,
    errorProvider: (String) -> String?,
    onError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(true) }
    @Suppress("UNCHECKED_CAST")
    val dataModel = definition.dataModel as IsValueDataModel<ValueDataObject, IsObjectDataModel<ValueDataObject>>
    val objectValues = valueObjectValuesFromValue(dataModel, value)
    val hasValue = objectValues != null
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = dataModel.Meta.name,
        countLabel = null,
        enabled = enabled,
        allowUnset = !required && enabled && hasValue,
        onUnset = { onValueChange(null) },
        onToggle = { expanded = !expanded },
        onAdd = if (enabled && !hasValue) {
            {
                expanded = true
                val defaults = createDefaultValueObjectValues(definition)
                onValueChange(dataModel.invoke(defaults))
            }
        } else null,
    )
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    if (expanded && objectValues != null) {
        dataModel.forEach { child ->
            val childValue = objectValues.original(child.index)
            val childPath = "$path.${child.name}"
            EditorInlineValue(
                label = child.name,
                path = childPath,
                definition = child.definition,
                value = childValue,
                enabled = enabled && (!definition.final || allowFinalEdit || value == null),
                required = child.definition.required,
                indent = indent + 1,
                state = state,
                parentRef = null,
                editorState = null,
                onValueChange = { updated ->
                    @Suppress("UNCHECKED_CAST")
                    val nextValues = objectValuesWithChange(
                        dataModel as IsTypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>,
                        objectValues as ObjectValues<Any, IsObjectDataModel<Any>>,
                        child.index,
                        updated,
                    ) as ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>>
                    onValueChange(dataModel.invoke(nextValues))
                },
                onError = { onError(childPath, it) },
                errorProvider = errorProvider,
                onPathError = onError,
            )
        }
    }
}

@Composable
private fun EditorMultiTypeField(
    label: String,
    path: String,
    definition: IsMultiTypeDefinition<TypeEnum<Any>, Any, *>,
    value: TypedValue<TypeEnum<Any>, Any>?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    onValueChange: (TypedValue<TypeEnum<Any>, Any>?) -> Unit,
    onError: (String?) -> Unit,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(true) }
    val typeCases = definition.typeEnum.cases()
    val currentType = value?.type
    val typeLabel = currentType?.name ?: "Select…"
    val canChangeType = enabled && !definition.typeIsFinal
    EditorCollapsibleHeader(
        label = label,
        required = required,
        indent = indent,
        expanded = expanded,
        typeLabel = "Type",
        countLabel = null,
        enabled = enabled,
        allowUnset = !required && enabled,
        onUnset = { onValueChange(null); onError(null) },
        onToggle = { expanded = !expanded },
        onAdd = null,
    )
    Row(
        modifier = Modifier.padding(start = (indent * 12 + 148).dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var typeExpanded by remember(path) { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.height(30.dp).widthIn(min = 120.dp).clickable(enabled = canChangeType) { typeExpanded = true },
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(typeLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
            typeCases.forEach { typeCase ->
                DropdownMenuItem(
                    text = { Text(typeCase.name, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        typeExpanded = false
                        val defaultValue = defaultValueForDefinition(definition.definition(typeCase))
                        if (defaultValue != null) {
                            val typedValue = createTypedValue(typeCase, defaultValue)
                            onValueChange(typedValue)
                            onError(validateValue(definition, typedValue, required))
                        }
                    },
                    enabled = canChangeType,
                )
            }
        }
    }
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = (indent * 12 + 148).dp))
    }
    value?.let { typedValue ->
        if (expanded) {
            val subDef = definition.definition(typedValue.type)
            if (subDef != null) {
                EditorInlineValue(
                    label = typedValue.type.name,
                    path = "$path.${typedValue.type.name}",
                    definition = subDef,
                    value = typedValue.value,
                    enabled = enabled && !definition.final,
                    required = required,
                    indent = indent + 1,
                    state = state,
                    parentRef = null,
                    editorState = null,
                    onValueChange = { updated ->
                        val innerValue = updated ?: defaultValueForDefinition(subDef)
                        if (innerValue != null) {
                            val sanitized = sanitizeValueForWrite(subDef, innerValue) ?: innerValue
                            val newTypedValue = createTypedValue(typedValue.type, sanitized)
                            onValueChange(newTypedValue)
                            onError(validateValue(definition, newTypedValue, required))
                        } else {
                            onValueChange(null)
                            onError(if (required) "Required." else null)
                        }
                    },
                    onError = { onPathError("$path.${typedValue.type.name}", it) },
                    errorProvider = errorProvider,
                    onPathError = onPathError,
                )
            }
        }
    }
}

@Composable
private fun EditorInlineValue(
    label: String,
    path: String,
    definition: IsPropertyDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    parentRef: AnyPropertyReference?,
    editorState: RecordEditState?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
) {
    when (definition) {
        is IsListDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorListField(
                label = label,
                path = path,
                definition = definition as IsListDefinition<Any, *>,
                value = value as? List<Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = null,
                onValueChange = onValueChange,
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
        }
        is IsSetDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorSetField(
                label = label,
                path = path,
                definition = definition as IsSetDefinition<Any, *>,
                value = value as? Set<Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = null,
                onValueChange = onValueChange,
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
        }
        is IsMapDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            val incMapRef = if (definition is IncrementingMapDefinition<*, *, *>) {
                parentRef as? IncMapReference<Comparable<Any>, Any, *>
            } else null
            @Suppress("UNCHECKED_CAST")
            EditorMapField(
                label = label,
                path = path,
                definition = definition as IsMapDefinition<Any, Any, *>,
                value = value as? Map<Any, Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = null,
                pendingAdds = editorState?.pendingIncMapValues(path) ?: emptyList(),
                onAddPending = if (incMapRef != null && editorState != null) {
                    { pending -> editorState.addIncMapValue(path, incMapRef, pending) }
                } else null,
                onRemovePending = if (incMapRef != null && editorState != null) {
                    { index -> editorState.removeIncMapValue(path, index) }
                } else null,
                focusPath = editorState?.pendingIncMapFocusPath,
                onConsumeFocus = { if (incMapRef != null && editorState != null) editorState.consumeIncMapFocus(path) },
                onValueChange = onValueChange,
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
        }
        is IsEmbeddedValuesDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val embeddedValues = value as? Values<IsValuesDataModel>
            EditorEmbeddedField(
                label = label,
                path = path,
                definition = definition,
                values = embeddedValues,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                defaultExpanded = true,
                error = null,
                canUnset = !required && enabled && embeddedValues != null,
                onUnset = { onValueChange(null) },
                onValueChange = onValueChange,
                allowFinalEdit = true,
                parentRef = parentRef,
                editorState = editorState,
                errorProvider = errorProvider,
                onError = onPathError,
            )
        }
        is IsEmbeddedObjectDefinition<*, *, *, *> -> {
            EditorEmbeddedObjectField(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                defaultExpanded = true,
                error = null,
                onValueChange = onValueChange,
                allowFinalEdit = true,
                errorProvider = errorProvider,
                onError = onPathError,
            )
        }
        is ValueObjectDefinition<*, *> -> {
            EditorValueObjectField(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = null,
                onValueChange = onValueChange,
                allowFinalEdit = true,
                errorProvider = errorProvider,
                onError = onPathError,
            )
        }
        is IsMultiTypeDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            EditorMultiTypeField(
                label = label,
                path = path,
                definition = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>,
                value = value as? TypedValue<TypeEnum<Any>, Any>,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = null,
                onValueChange = onValueChange,
                onError = onError,
                errorProvider = errorProvider,
                onPathError = onPathError,
            )
        }
        is IsReferenceDefinition<*, *> -> {
            ReferenceEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = errorProvider(path),
                state = state,
                onValueChange = onValueChange,
                onError = { onPathError(path, it) },
            )
        }
        is IsPropertyDefinition<*> -> {
            EditorValueField(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = errorProvider(path),
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = { onPathError(path, it) },
            )
        }
    }
}

@Composable
private fun EditorListField(
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
                val newItem = defaultValueForDefinition(definition.valueDefinition) ?: return@EditorCollapsibleHeader
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
                            val next = listValue.toMutableList()
                            next.removeAt(index)
                            onValueChange(next)
                            onError(validateValue(definition, next, required))
                        },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSetField(
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
                val newItem = defaultValueForDefinition(definition.valueDefinition) ?: return@EditorCollapsibleHeader
                if (setValue.contains(newItem)) return@EditorCollapsibleHeader
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
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorMapField(
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
    val contentStart = (indent * 12 + if (isIncMap) 140 else 148).dp
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
                val newValue = defaultValueForDefinition(definition.valueDefinition) ?: return@EditorCollapsibleHeader
                if (isIncMap && onAddPending != null) {
                    onAddPending(newValue)
                } else {
                    val key = defaultMapKey(definition.keyDefinition, mapValue.keys) ?: return@EditorCollapsibleHeader
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
                        if (updated == null || onRemovePending == null) return@EditorInlineValue
                        onRemovePending(index)
                        onAddPending?.invoke(updated)
                    },
                    onError = onError,
                    errorProvider = errorProvider,
                    onPathError = onPathError,
                )
                if (enabled && onRemovePending != null) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { onRemovePending(index) },
                                modifier = Modifier.size(22.dp),
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
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove entry", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorCollapsibleHeader(
    label: String,
    required: Boolean,
    indent: Int,
    expanded: Boolean,
    typeLabel: String,
    countLabel: String?,
    enabled: Boolean,
    allowUnset: Boolean,
    onUnset: (() -> Unit)?,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    val headerLabel = buildString {
        append(label)
        if (required) append(" *")
        if (!countLabel.isNullOrBlank()) append(" ($countLabel)")
    }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 12).dp, top = 2.dp)
            .offset(x = (-6).dp)
            .clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(headerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onAdd != null) {
                OutlinedButton(
                    onClick = onAdd,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(12.dp))
                        Text(
                            "Add",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(y = (-1).dp),
                        )
                    }
                }
            }
            if (allowUnset && onUnset != null) {
                IconButton(onClick = onUnset, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Unset", modifier = Modifier.size(14.dp))
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun EditorTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    enabled: Boolean,
    indent: Int = 0,
    placeholder: String? = null,
    error: String? = null,
    allowUnset: Boolean = false,
    onUnset: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(label) {
            focusRequester.requestFocus()
        }
    }
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = placeholder,
                isError = error != null,
                focusRequester = if (autoFocus) focusRequester else null,
                trailingContent = trailingContent,
            )
            if (allowUnset && onUnset != null) {
                IconButton(onClick = onUnset, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Unset", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorRowShell(
    label: String,
    required: Boolean,
    indent: Int,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = (indent * 12).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labelText = if (required) "$label *" else label
            Text(
                labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(140.dp),
            )
            content()
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 148.dp),
            )
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String?,
    isError: Boolean,
    focusRequester: FocusRequester? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val background = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(30.dp).widthIn(min = 180.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.SansSerif),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().let { base ->
                        if (focusRequester != null) base.focusRequester(focusRequester) else base
                    },
                )
                if (value.isBlank() && placeholder != null) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
            if (trailingContent != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    trailingContent()
                }
            }
        }
    }
}

private sealed class ParseResult {
    data class Value(val value: Any) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

private fun parseValue(definition: IsPropertyDefinition<*>, raw: String): ParseResult {
    return when (definition) {
        is IsValueDefinition<*, *> -> {
            try {
                @Suppress("UNCHECKED_CAST")
                val def = definition as IsValueDefinition<Any, IsPropertyContext>
                ParseResult.Value(def.fromString(raw))
            } catch (e: Throwable) {
                ParseResult.Error("Invalid format.")
            }
        }
        else -> ParseResult.Error("Unsupported value.")
    }
}

private fun parseSimpleValue(definition: IsSimpleValueDefinition<*, *>, raw: String): ParseResult {
    return try {
        @Suppress("UNCHECKED_CAST")
        val def = definition as IsSimpleValueDefinition<Any, IsPropertyContext>
        ParseResult.Value(def.fromString(raw))
    } catch (e: Throwable) {
        ParseResult.Error("Invalid key.")
    }
}

private fun formatValue(definition: IsPropertyDefinition<*>, value: Any): String {
    return when (definition) {
        is IsValueDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<Any, IsPropertyContext>).asString(value)
        }
        else -> value.toString()
    }
}

private fun validateValue(definition: IsPropertyDefinition<*>, value: Any?, required: Boolean): String? {
    if (value == null && required) return "Required."
    return try {
        @Suppress("UNCHECKED_CAST")
        (definition as IsPropertyDefinition<Any>).validateWithRef(null, value) { null }
        null
    } catch (e: Throwable) {
        formatValidationMessage(e)
    }
}

private fun formatValidationMessage(error: Throwable): String {
    return when (error) {
        is ValidationUmbrellaException -> {
            val messages = error.exceptions.mapNotNull { it.message }.map(::stripPropertyPrefix)
            messages.firstOrNull() ?: "Invalid value."
        }
        is ValidationException -> error.message?.let(::stripPropertyPrefix) ?: "Invalid value."
        else -> error.message ?: "Invalid value."
    }
}

private fun stripPropertyPrefix(message: String): String {
    val prefix = "Property «"
    if (!message.startsWith(prefix)) return message
    val end = message.indexOf("» ")
    if (end == -1) return message
    return message.substring(end + 2).trim()
}

private fun <DM : IsValuesDataModel> copyWithValue(
    values: Values<DM>,
    index: UInt,
    newValue: Any?,
): Values<DM> {
    val payload = newValue ?: Unit
    return values.copy {
        ValuesCollectorContext.add(ValueItem(index, payload))
    }
}

@Suppress("UNCHECKED_CAST")
private fun <DM : IsValuesDataModel> valuesWithChange(
    values: Values<DM>,
    ref: AnyPropertyReference,
    newValue: Any?,
): Values<DM> {
    val index = (ref as IsPropertyReferenceForValues<Any, Any, *, *>).index
    return copyWithValue(values, index, newValue)
}

@Suppress("UNCHECKED_CAST")
private fun <DM : IsValuesDataModel> sanitizeValuesForWrite(values: Values<DM>): Values<DM> {
    val typed = values.dataModel as TypedValuesDataModel<DM>
    return typed.create(setDefaults = false) {
        for ((index, value) in values) {
            val wrapper = values.dataModel[index] ?: continue
            val sanitized = sanitizeValueForWrite(wrapper.definition, value) ?: Unit
            ValuesCollectorContext.add(ValueItem(index, sanitized))
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun sanitizeValueForWrite(definition: IsPropertyDefinition<*>, value: Any?): Any? {
    if (value == null) return null
    return when (definition) {
        is IsEmbeddedValuesDefinition<*, *> -> {
            (value as? Values<IsValuesDataModel>)?.let { sanitizeValuesForWrite(it) } ?: value
        }
        is IsEmbeddedObjectDefinition<*, *, *, *> -> {
            when (value) {
                is ValueDataObjectWithValues -> value.values.toDataObject()
                is ObjectValues<*, *> -> value.toDataObject()
                else -> value
            }
        }
        is IsListDefinition<*, *> -> {
            (value as? List<Any>)?.map { item ->
                sanitizeValueForWrite(definition.valueDefinition, item) ?: Unit
            } ?: value
        }
        is IsSetDefinition<*, *> -> {
            (value as? Set<Any>)?.map { item ->
                sanitizeValueForWrite(definition.valueDefinition, item) ?: Unit
            }?.toSet() ?: value
        }
        is IsMapDefinition<*, *, *> -> {
            (value as? Map<Any, Any>)?.mapValues { entry ->
                sanitizeValueForWrite(definition.valueDefinition, entry.value) ?: Unit
            } ?: value
        }
        is IsMultiTypeDefinition<*, *, *> -> {
            val typedValue = value as? TypedValue<TypeEnum<Any>, Any> ?: return value
            val multiDef = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>
            val subDef = multiDef.definition(typedValue.type)
            if (subDef == null) typedValue else {
                val innerValue = sanitizeValueForWrite(subDef, typedValue.value) ?: typedValue.value
                createTypedValue(typedValue.type, innerValue)
            }
        }
        else -> value
    }
}

@Suppress("UNCHECKED_CAST")
private fun resolvePropertyValue(ref: AnyPropertyReference, values: Any): Any? {
    val typedRef = ref as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
    return try {
        typedRef.resolve(values)
    } catch (_: Throwable) {
        when {
            values is Values<*> && ref is IsPropertyReferenceForValues<*, *, *, *> -> values.original(ref.index)
            values is ObjectValues<*, *> && ref is IsPropertyReferenceForValues<*, *, *, *> -> values.original(ref.index)
            else -> null
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildReferencePair(
    ref: AnyPropertyReference,
    value: Any?,
): IsReferenceValueOrNullPair<Any> {
    val typedRef = ref as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
    return IsReferenceValueOrNullPair.create {
        reference with typedRef
        if (value != null) {
            this.value with value
        }
    }.toDataObject()
}

@Suppress("UNCHECKED_CAST")
private fun <E : TypeEnum<T>, T : Any> createTypedValue(
    type: E,
    value: Any,
): TypedValue<E, T> {
    return type.invoke(value as T)
}

@Suppress("UNCHECKED_CAST")
private fun createDefaultValues(definition: IsEmbeddedValuesDefinition<*, *>): Values<IsValuesDataModel> {
    val dataModel = definition.dataModel
    val typed = dataModel as TypedValuesDataModel<IsValuesDataModel>
    return typed.create(setDefaults = true) {}
}

@Suppress("UNCHECKED_CAST")
private fun createDefaultObjectValues(definition: IsEmbeddedObjectDefinition<*, *, *, *>): ObjectValues<Any, IsObjectDataModel<Any>> {
    val dataModel = definition.dataModel as IsTypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>
    val defaultValue = definition.default
    return if (defaultValue != null) {
        (dataModel as IsObjectDataModel<Any>).asValues(defaultValue)
    } else {
        val typed = dataModel as? TypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>
        typed?.create(setDefaults = true) {} ?: dataModel.emptyValues()
    }
}

@Suppress("UNCHECKED_CAST")
private fun createDefaultValueObjectValues(definition: ValueObjectDefinition<*, *>): ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>> {
    val dataModel = definition.dataModel as IsValueDataModel<ValueDataObject, IsObjectDataModel<ValueDataObject>>
    val defaultValue = definition.default
    return if (defaultValue != null) {
        (dataModel as IsObjectDataModel<ValueDataObject>).asValues(defaultValue)
    } else {
        val typed = dataModel as? TypedObjectDataModel<ValueDataObject, IsObjectDataModel<ValueDataObject>, IsPropertyContext, IsPropertyContext>
        typed?.create(setDefaults = true) {} ?: dataModel.emptyValues()
    }
}

@Suppress("UNCHECKED_CAST")
private fun objectValuesFromValue(
    dataModel: IsTypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>,
    value: Any?,
): ObjectValues<Any, IsObjectDataModel<Any>>? {
    return when (value) {
        is ValueDataObjectWithValues -> value.values as ObjectValues<Any, IsObjectDataModel<Any>>
        is ObjectValues<*, *> -> value as ObjectValues<Any, IsObjectDataModel<Any>>
        null -> null
        else -> (dataModel as IsObjectDataModel<Any>).asValues(value)
    }
}

@Suppress("UNCHECKED_CAST")
private fun valueObjectValuesFromValue(
    dataModel: IsValueDataModel<ValueDataObject, IsObjectDataModel<ValueDataObject>>,
    value: Any?,
): ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>>? {
    return when (value) {
        is ValueDataObjectWithValues -> value.values as ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>>
        is ObjectValues<*, *> -> value as ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>>
        null -> null
        else -> (dataModel as IsObjectDataModel<ValueDataObject>).asValues(value as ValueDataObject)
    }
}

@Suppress("UNCHECKED_CAST")
private fun objectValuesWithChange(
    dataModel: IsTypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>,
    values: ObjectValues<Any, IsObjectDataModel<Any>>,
    index: UInt,
    newValue: Any?,
): ObjectValues<Any, IsObjectDataModel<Any>> {
    val typed = dataModel as? TypedObjectDataModel<Any, IsObjectDataModel<Any>, IsPropertyContext, IsPropertyContext>
    if (typed != null) {
        return typed.create(setDefaults = false) {
            for ((itemIndex, itemValue) in values) {
                if (itemIndex == index) continue
                ValuesCollectorContext.add(ValueItem(itemIndex, itemValue))
            }
            ValuesCollectorContext.add(ValueItem(index, newValue ?: Unit))
        }
    }
    values.mutate { arrayOf(ValueItem(index, newValue ?: Unit)) }
    return values
}

@Suppress("UNCHECKED_CAST")
private fun defaultValueForDefinition(definition: IsSubDefinition<*, *>?): Any? {
    if (definition == null) return null
    if (definition is HasDefaultValueDefinition<*>) {
        definition.default?.let { return it }
    }
    return when (definition) {
        is StringDefinition -> ""
        is BooleanDefinition -> false
        is NumberDefinition<*> -> definition.fromString("0")
        is EnumDefinition<*> -> definition.enum.cases().firstOrNull()
        is DateDefinition -> definition.minValue ?: DateDefinition.nowUTC()
        is TimeDefinition -> definition.minValue ?: LocalTime(0, 0, 0)
        is DateTimeDefinition -> definition.minValue ?: DateTimeDefinition.nowUTC()
        is IsEmbeddedValuesDefinition<*, *> -> createDefaultValues(definition)
        is IsEmbeddedObjectDefinition<*, *, *, *> -> createDefaultObjectValues(definition)
        is IsListDefinition<*, *> -> emptyList<Any>()
        is IsSetDefinition<*, *> -> emptySet<Any>()
        is IsMapDefinition<*, *, *> -> emptyMap<Any, Any>()
        is IsMultiTypeDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            val multiDefinition = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>
            val type = multiDefinition.typeEnum.cases().firstOrNull() ?: return null
            val subDef = multiDefinition.definition(type)
            val inner = defaultValueForDefinition(subDef)
            if (inner != null) createTypedValue(type, inner) else null
        }
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun defaultMapKey(definition: IsSimpleValueDefinition<Any, *>, existingKeys: Collection<Any>): Any? {
    return when (definition) {
        is StringDefinition -> {
            var index = 0
            while (true) {
                val candidate = if (index == 0) "new" else "new$index"
                if (!existingKeys.contains(candidate)) return candidate
                index++
            }
        }
        is NumberDefinition<*> -> {
            val maxExisting = existingKeys.mapNotNull { key ->
                when (key) {
                    is Byte -> key.toLong()
                    is Short -> key.toLong()
                    is Int -> key.toLong()
                    is Long -> key
                    is Float -> key.toLong()
                    is Double -> key.toLong()
                    is UByte -> key.toLong()
                    is UShort -> key.toLong()
                    is UInt -> key.toLong()
                    is ULong -> key.toLong()
                    is Number -> key.toLong()
                    else -> null
                }
            }.maxOrNull()
            var number = (maxExisting ?: -1L) + 1L
            while (true) {
                val candidate = definition.fromString(number.toString())
                if (!existingKeys.contains(candidate)) return candidate
                number++
            }
        }
        is EnumDefinition<*> -> {
            val cases = definition.enum.cases()
            cases.firstOrNull { !existingKeys.contains(it) } ?: cases.firstOrNull()
        }
        is BooleanDefinition -> {
            if (!existingKeys.contains(false)) false else if (!existingKeys.contains(true)) true else null
        }
        is DateDefinition -> {
            val candidate = definition.minValue ?: DateDefinition.nowUTC()
            if (!existingKeys.contains(candidate)) candidate else null
        }
        is TimeDefinition -> {
            val candidate = definition.minValue ?: LocalTime(0, 0, 0)
            if (!existingKeys.contains(candidate)) candidate else null
        }
        is DateTimeDefinition -> {
            val candidate = definition.minValue ?: DateTimeDefinition.nowUTC()
            if (!existingKeys.contains(candidate)) candidate else null
        }
        else -> null
    }
}
