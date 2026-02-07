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
internal fun EditorField(
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
                onUpdatePending = if (incMapRef != null) {
                    { index, value -> editorState.updateIncMapValue(path, index, value) }
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
        is ValueObjectDefinition<*, *> -> {
            EditorValueObjectField(
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
                errorProvider = errorProvider,
                onPathError = onError,
                onValueChange = { editorState.updateValue(ref, it) },
                onError = { onError(path, it) },
            )
        }
    }
}

@Composable
internal fun EditorInlineValue(
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
                onUpdatePending = if (incMapRef != null && editorState != null) {
                    { index, pending -> editorState.updateIncMapValue(path, index, pending) }
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
                errorProvider = errorProvider,
                onPathError = onPathError,
                onValueChange = onValueChange,
                onError = { onPathError(path, it) },
            )
        }
    }
}
