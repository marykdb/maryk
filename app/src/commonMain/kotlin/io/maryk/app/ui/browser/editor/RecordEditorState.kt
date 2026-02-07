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
