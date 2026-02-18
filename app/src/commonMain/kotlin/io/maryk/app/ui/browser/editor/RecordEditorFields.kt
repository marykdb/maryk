package io.maryk.app.ui.browser.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.maryk.app.state.BrowserState
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values

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
