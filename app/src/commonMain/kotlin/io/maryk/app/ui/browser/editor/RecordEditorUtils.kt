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
import kotlinx.datetime.toInstant
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
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.GeoPointDefinition
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
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.GeoPoint
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
import kotlin.time.Duration.Companion.nanoseconds
internal sealed class ParseResult {
    data class Value(val value: Any) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

internal fun parseValue(definition: IsPropertyDefinition<*>, raw: String): ParseResult {
    return when (definition) {
        is IsValueDefinition<*, *> -> {
            try {
                @Suppress("UNCHECKED_CAST")
                val def = definition as IsValueDefinition<Any, IsPropertyContext>
                ParseResult.Value(def.fromString(raw))
            } catch (_: Throwable) {
                ParseResult.Error("Invalid format.")
            }
        }
        else -> ParseResult.Error("Unsupported value.")
    }
}

internal fun parseSimpleValue(definition: IsSimpleValueDefinition<*, *>, raw: String): ParseResult {
    return try {
        @Suppress("UNCHECKED_CAST")
        val def = definition as IsSimpleValueDefinition<Any, IsPropertyContext>
        ParseResult.Value(def.fromString(raw))
    } catch (_: Throwable) {
        ParseResult.Error("Invalid key.")
    }
}

internal fun formatValue(definition: IsPropertyDefinition<*>, value: Any): String {
    return when (definition) {
        is IsValueDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<Any, IsPropertyContext>).asString(value)
        }
        else -> value.toString()
    }
}

internal fun validateValue(definition: IsPropertyDefinition<*>, value: Any?, required: Boolean): String? {
    if (value == null && required) return "Required."
    return try {
        @Suppress("UNCHECKED_CAST")
        (definition as IsPropertyDefinition<Any>).validateWithRef(null, value) { null }
        null
    } catch (e: Throwable) {
        formatValidationMessage(e)
    }
}

internal fun formatValidationMessage(error: Throwable): String {
    return when (error) {
        is ValidationUmbrellaException -> {
            val messages = error.exceptions.mapNotNull { it.message }.map(::stripPropertyPrefix)
            messages.firstOrNull() ?: "Invalid value."
        }
        is ValidationException -> error.message?.let(::stripPropertyPrefix) ?: "Invalid value."
        else -> error.message ?: "Invalid value."
    }
}

internal fun stripPropertyPrefix(message: String): String {
    val prefix = "Property «"
    if (!message.startsWith(prefix)) return message
    val end = message.indexOf("» ")
    if (end == -1) return message
    return message.substring(end + 2).trim()
}

internal fun <DM : IsValuesDataModel> copyWithValue(
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
internal fun <DM : IsValuesDataModel> valuesWithChange(
    values: Values<DM>,
    ref: AnyPropertyReference,
    newValue: Any?,
): Values<DM> {
    val index = (ref as IsPropertyReferenceForValues<Any, Any, *, *>).index
    return copyWithValue(values, index, newValue)
}

@Suppress("UNCHECKED_CAST")
internal fun <DM : IsValuesDataModel> sanitizeValuesForWrite(values: Values<DM>): Values<DM> {
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
internal fun sanitizeValueForWrite(definition: IsPropertyDefinition<*>, value: Any?): Any? {
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
internal fun resolvePropertyValue(ref: AnyPropertyReference, values: Any): Any? {
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
internal fun buildReferencePair(
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
internal fun <E : TypeEnum<T>, T : Any> createTypedValue(
    type: E,
    value: Any,
): TypedValue<E, T> {
    return type.invoke(value as T)
}

@Suppress("UNCHECKED_CAST")
internal fun createDefaultValues(definition: IsEmbeddedValuesDefinition<*, *>): Values<IsValuesDataModel> {
    val dataModel = definition.dataModel
    val typed = dataModel as TypedValuesDataModel<IsValuesDataModel>
    return typed.create(setDefaults = true) {}
}

@Suppress("UNCHECKED_CAST")
internal fun createDefaultObjectValues(definition: IsEmbeddedObjectDefinition<*, *, *, *>): ObjectValues<Any, IsObjectDataModel<Any>> {
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
internal fun createDefaultValueObjectValues(definition: ValueObjectDefinition<*, *>): ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>> {
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
internal fun objectValuesFromValue(
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
internal fun valueObjectValuesFromValue(
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
internal fun objectValuesWithChange(
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
internal fun defaultValueForDefinition(definition: IsSubDefinition<*, *>?): Any? {
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
        is FixedBytesDefinition -> definition.fromNativeType(ByteArray(definition.byteSize))
        is FlexBytesDefinition -> definition.fromString("")
        is GeoPointDefinition -> GeoPoint(0, 0)
        is IsReferenceDefinition<*, *> -> definition.fromNativeType(ByteArray(definition.byteSize))
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
internal fun defaultMapKey(definition: IsSimpleValueDefinition<Any, *>, existingKeys: Collection<Any>): Any? {
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
                val candidate = runCatching { definition.fromString(number.toString()) }.getOrNull() ?: return null
                if (!existingKeys.contains(candidate)) return candidate
                if (number == Long.MAX_VALUE) return null
                number++
            }
        }
        is EnumDefinition<*> -> {
            val cases = definition.enum.cases()
            cases.firstOrNull { !existingKeys.contains(it) }
        }
        is BooleanDefinition -> {
            if (!existingKeys.contains(false)) false else if (!existingKeys.contains(true)) true else null
        }
        is DateDefinition -> {
            nextDateCandidate(definition, existingKeys)
        }
        is TimeDefinition -> {
            nextTimeCandidate(definition, existingKeys)
        }
        is DateTimeDefinition -> {
            nextDateTimeCandidate(definition, existingKeys)
        }
        else -> {
            val candidate = defaultValueForDefinition(definition)
            if (candidate != null && !existingKeys.contains(candidate)) candidate else null
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun defaultSetItem(definition: IsSubDefinition<*, *>, existingValues: Set<Any>): Any? {
    val defaultValue = defaultValueForDefinition(definition) ?: return null
    if (!existingValues.contains(defaultValue)) return defaultValue

    return when (definition) {
        is BooleanDefinition -> {
            if (!existingValues.contains(false)) false else if (!existingValues.contains(true)) true else null
        }
        is EnumDefinition<*> -> {
            definition.enum.cases().firstOrNull { !existingValues.contains(it) }
        }
        is StringDefinition -> {
            var index = 0
            while (true) {
                val candidate = if (index == 0) "new" else "new$index"
                if (!existingValues.contains(candidate)) return candidate
                index++
            }
        }
        is NumberDefinition<*> -> {
            val maxExisting = existingValues.mapNotNull { key ->
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
                val candidate = runCatching { definition.fromString(number.toString()) }.getOrNull() ?: return null
                if (!existingValues.contains(candidate)) return candidate
                if (number == Long.MAX_VALUE) return null
                number++
            }
        }
        is DateDefinition -> {
            nextDateCandidate(definition, existingValues)
        }
        is TimeDefinition -> {
            nextTimeCandidate(definition, existingValues)
        }
        is DateTimeDefinition -> {
            nextDateTimeCandidate(definition, existingValues)
        }
        else -> null
    }
}

internal fun mergeDateTimeSelection(
    existing: LocalDateTime?,
    selectedDate: LocalDate,
    selectedHour: Int,
    selectedMinute: Int,
): LocalDateTime {
    val second = existing?.second ?: 0
    val nanosecond = existing?.nanosecond ?: 0
    return selectedDate.atTime(LocalTime(selectedHour, selectedMinute, second, nanosecond))
}

private fun nextDateCandidate(definition: DateDefinition, existingValues: Collection<Any>): LocalDate? {
    val minDate = definition.minValue ?: DateDefinition.nowUTC()
    val maxDate = definition.maxValue ?: DateDefinition.MAX
    if (!existingValues.contains(minDate)) return minDate

    val maxExistingEpochDays = existingValues.filterIsInstance<LocalDate>()
        .map(LocalDate::toEpochDays)
        .maxOrNull() ?: minDate.toEpochDays()
    val candidateEpochDays = maxExistingEpochDays + 1
    if (candidateEpochDays > maxDate.toEpochDays()) return null
    return LocalDate.fromEpochDays(candidateEpochDays)
}

private fun nextTimeCandidate(definition: TimeDefinition, existingValues: Collection<Any>): LocalTime? {
    val minValue = definition.minValue ?: LocalTime(0, 0, 0)
    if (!existingValues.contains(minValue)) return minValue

    val minUnit = minValue.toTimeUnit(definition.precision)
    val maxUnit = (definition.maxValue ?: defaultMaxTime(definition.precision)).toTimeUnit(definition.precision)
    val maxExistingUnit = existingValues.filterIsInstance<LocalTime>()
        .map { it.toTimeUnit(definition.precision) }
        .maxOrNull() ?: minUnit
    if (maxExistingUnit >= maxUnit) return null
    return timeFromUnit(maxExistingUnit + 1, definition.precision)
}

private fun nextDateTimeCandidate(definition: DateTimeDefinition, existingValues: Collection<Any>): LocalDateTime? {
    val minValue = definition.minValue ?: DateTimeDefinition.nowUTC()
    if (!existingValues.contains(minValue)) return minValue

    return when (definition.precision) {
        TimePrecision.SECONDS,
        TimePrecision.MILLIS -> {
            val minUnit = minValue.toDateTimeUnit(definition.precision)
            val maxUnit = (definition.maxValue ?: defaultMaxDateTime(definition.precision)).toDateTimeUnit(definition.precision)
            val maxExistingUnit = existingValues.filterIsInstance<LocalDateTime>()
                .map { it.toDateTimeUnit(definition.precision) }
                .maxOrNull() ?: minUnit
            if (maxExistingUnit >= maxUnit) null else dateTimeFromUnit(maxExistingUnit + 1, definition.precision)
        }
        TimePrecision.NANOS -> {
            val maxInstant = (definition.maxValue ?: defaultMaxDateTime(definition.precision)).toInstant(TimeZone.UTC)
            val maxExistingInstant = existingValues.filterIsInstance<LocalDateTime>()
                .map { it.toInstant(TimeZone.UTC) }
                .maxOrNull() ?: minValue.toInstant(TimeZone.UTC)
            val candidate = maxExistingInstant + 1.nanoseconds
            if (candidate > maxInstant) null else candidate.toLocalDateTime(TimeZone.UTC)
        }
    }
}

private fun LocalTime.toTimeUnit(precision: TimePrecision): Long = when (precision) {
    TimePrecision.SECONDS -> this.toSecondOfDay().toLong()
    TimePrecision.MILLIS -> this.toMillisecondOfDay().toLong()
    TimePrecision.NANOS -> this.toNanosecondOfDay()
}

private fun timeFromUnit(unit: Long, precision: TimePrecision): LocalTime = when (precision) {
    TimePrecision.SECONDS -> LocalTime.fromSecondOfDay(unit.toInt())
    TimePrecision.MILLIS -> LocalTime.fromMillisecondOfDay(unit.toInt())
    TimePrecision.NANOS -> LocalTime.fromNanosecondOfDay(unit)
}

private fun defaultMaxTime(precision: TimePrecision): LocalTime = when (precision) {
    TimePrecision.SECONDS -> TimeDefinition.MAX_IN_SECONDS
    TimePrecision.MILLIS -> TimeDefinition.MAX_IN_MILLIS
    TimePrecision.NANOS -> TimeDefinition.MAX_IN_NANOS
}

private fun LocalDateTime.toDateTimeUnit(precision: TimePrecision): Long {
    val instant = this.toInstant(TimeZone.UTC)
    return when (precision) {
        TimePrecision.SECONDS -> instant.epochSeconds
        TimePrecision.MILLIS -> instant.toEpochMilliseconds()
        TimePrecision.NANOS -> error("Nanos precision is handled separately to avoid overflow.")
    }
}

private fun dateTimeFromUnit(unit: Long, precision: TimePrecision): LocalDateTime {
    val instant = when (precision) {
        TimePrecision.SECONDS -> Instant.fromEpochSeconds(unit)
        TimePrecision.MILLIS -> Instant.fromEpochMilliseconds(unit)
        TimePrecision.NANOS -> error("Nanos precision is handled separately to avoid overflow.")
    }
    return instant.toLocalDateTime(TimeZone.UTC)
}

private fun defaultMaxDateTime(precision: TimePrecision): LocalDateTime = when (precision) {
    TimePrecision.SECONDS -> DateTimeDefinition.MAX_IN_SECONDS
    TimePrecision.MILLIS -> DateTimeDefinition.MAX_IN_MILLIS
    TimePrecision.NANOS -> DateTimeDefinition.MAX_IN_NANOS
}
