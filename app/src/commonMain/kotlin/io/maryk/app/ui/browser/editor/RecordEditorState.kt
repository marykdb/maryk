package io.maryk.app.ui.browser.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.values.Values

internal class RecordEditState(
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
        } else if (showValidationErrors || shouldShowLiveError(message)) {
            errors[path] = message
        }
    }

    private fun shouldShowLiveError(message: String): Boolean {
        return when (message) {
            "Invalid format.",
            "Invalid key.",
            "Item already exists.",
            "Key already exists.",
            "No available key values for this map.",
            "No available unique values for this set." -> true
            else -> false
        }
    }

    fun updateValue(
        ref: AnyPropertyReference,
        value: Any?,
    ) {
        clearErrorsAtOrUnder(ref.completeName)
        val index = propertyIndex(ref)
        currentValues = copyWithValue(currentValues, index, value)
    }

    fun pendingIncMapValues(path: String): List<Any> {
        return pendingIncMapAdds[path] ?: emptyList()
    }

    fun addIncMapValue(path: String, ref: IncMapReference<Comparable<Any>, Any, *>, value: Any) {
        clearErrorsAtOrUnder(path)
        val list = pendingIncMapAdds.getOrPut(path) { mutableStateListOf() }
        list.add(0, value)
        pendingIncMapRefs[path] = ref
        pendingIncMapFocusPath = path
    }

    fun removeIncMapValue(path: String, index: Int) {
        clearErrorsAtOrUnder(path)
        val list = pendingIncMapAdds[path] ?: return
        if (index in list.indices) {
            list.removeAt(index)
        }
        if (list.isEmpty()) {
            pendingIncMapAdds.remove(path)
            pendingIncMapRefs.remove(path)
            if (pendingIncMapFocusPath == path) {
                pendingIncMapFocusPath = null
            }
        }
    }

    fun updateIncMapValue(path: String, index: Int, value: Any) {
        clearErrorsAtOrUnder(path)
        val list = pendingIncMapAdds[path] ?: return
        if (index in list.indices) {
            list[index] = value
        }
    }

    private fun clearErrorsAtOrUnder(path: String) {
        val childPrefixes = listOf("$path.", "$path[", "$path.^")
        val keysToRemove = errors.keys.filter { key ->
            key == path || childPrefixes.any { prefix -> key.startsWith(prefix) }
        }
        keysToRemove.forEach { errors.remove(it) }
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
        validatePendingIncMapAdds()
        return errors.isEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun validatePendingIncMapAdds() {
        pendingIncMapAdds.forEach { (path, values) ->
            val ref = pendingIncMapRefs[path] ?: return@forEach
            val valueDefinition = ref.propertyDefinition.definition.valueDefinition
            values.forEachIndexed { index, value ->
                validateItem("$path.^$index.value", valueDefinition, value)
            }
        }
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
                val valueDefinition = ref.propertyDefinition.definition.valueDefinition
                val addValues = values.asReversed().map { pending ->
                    @Suppress("UNCHECKED_CAST")
                    val definition = valueDefinition as IsPropertyDefinition<*>
                    sanitizeValueForWrite(definition, pending) ?: pending
                }
                changes += IncMapChange(ref.change(addValues = addValues))
            }
        }
        return changes
    }
}
