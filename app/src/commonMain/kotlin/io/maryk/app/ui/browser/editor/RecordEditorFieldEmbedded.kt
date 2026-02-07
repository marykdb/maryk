package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.maryk.app.data.KEY_ORDER_TOKEN
import io.maryk.app.data.ScanQueryParser
import io.maryk.app.data.buildRequestContext
import io.maryk.app.data.buildSummary
import io.maryk.app.data.serializeRecordToYaml
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
@Composable
internal fun EditorEmbeddedField(
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
internal fun EditorEmbeddedObjectField(
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
internal fun EditorValueObjectField(
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
                enabled = enabled && (!child.definition.final || allowFinalEdit || childValue == null),
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
internal fun EditorMultiTypeField(
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
    val canChangeType = enabled && (!definition.typeIsFinal || currentType == null)
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
            modifier = Modifier.height(30.dp).widthIn(min = 120.dp).handPointer().clickable(enabled = canChangeType) { typeExpanded = true },
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
                    enabled = enabled,
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
