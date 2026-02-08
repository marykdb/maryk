package io.maryk.app.ui.browser.editor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.ValuesCollectorContext
import maryk.core.models.asValues
import maryk.core.models.emptyValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
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
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.Values
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

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
            val maxSize = definition.maxSize?.toInt()
            var index = 0
            while (index < MAX_STRING_CANDIDATE_ATTEMPTS) {
                val candidate = nextStringCandidate(definition, index)
                if (maxSize != null && candidate.length > maxSize) {
                    index++
                    continue
                }
                if (isValidDefinitionValue(definition, candidate) && !existingKeys.contains(candidate)) return candidate
                index++
            }
            null
        }
        is NumberDefinition<*> -> nextUniqueNumberCandidate(definition, existingKeys)
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
        is FixedBytesDefinition -> nextUniqueFixedBytesCandidate(definition, existingKeys)
        is FlexBytesDefinition -> nextUniqueFlexBytesCandidate(definition, existingKeys)
        is IsReferenceDefinition<*, *> -> nextUniqueReferenceCandidate(definition, existingKeys)
        else -> {
            val candidate = defaultValueForDefinition(definition)
            if (candidate != null && isValidDefinitionValue(definition, candidate) && !existingKeys.contains(candidate)) {
                candidate
            } else {
                null
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun defaultSetItem(definition: IsSubDefinition<*, *>, existingValues: Set<Any>): Any? {
    val defaultValue = defaultValueForDefinition(definition)
    if (defaultValue != null && !existingValues.contains(defaultValue)) {
        val isValid = runCatching {
            @Suppress("UNCHECKED_CAST")
            (definition as? IsPropertyDefinition<Any>)?.validateWithRef(null, defaultValue) { null }
        }.isSuccess
        if (isValid) return defaultValue
    }

    return when (definition) {
        is BooleanDefinition -> {
            if (!existingValues.contains(false)) false else if (!existingValues.contains(true)) true else null
        }
        is EnumDefinition<*> -> {
            definition.enum.cases().firstOrNull { !existingValues.contains(it) }
        }
        is StringDefinition -> {
            val maxSize = definition.maxSize?.toInt()
            var index = 0
            while (index < MAX_STRING_CANDIDATE_ATTEMPTS) {
                val candidate = nextStringCandidate(definition, index)
                if (maxSize != null && candidate.length > maxSize) {
                    index++
                    continue
                }
                if (isValidDefinitionValue(definition, candidate) && !existingValues.contains(candidate)) return candidate
                index++
            }
            null
        }
        is NumberDefinition<*> -> nextUniqueNumberCandidate(definition, existingValues)
        is DateDefinition -> {
            nextDateCandidate(definition, existingValues)
        }
        is TimeDefinition -> {
            nextTimeCandidate(definition, existingValues)
        }
        is DateTimeDefinition -> {
            nextDateTimeCandidate(definition, existingValues)
        }
        is FixedBytesDefinition -> nextUniqueFixedBytesCandidate(definition, existingValues)
        is FlexBytesDefinition -> nextUniqueFlexBytesCandidate(definition, existingValues)
        is IsReferenceDefinition<*, *> -> nextUniqueReferenceCandidate(definition, existingValues)
        is IsMultiTypeDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            val multiDefinition = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>
            multiDefinition.typeEnum.cases().firstNotNullOfOrNull { typeCase ->
                val subDefinition = multiDefinition.definition(typeCase) ?: return@firstNotNullOfOrNull null
                val inner = defaultSetItem(subDefinition, emptySet()) ?: defaultValueForDefinition(subDefinition)
                if (inner == null) return@firstNotNullOfOrNull null
                val typed = createTypedValue(typeCase, inner)
                if (isValidDefinitionValue(definition, typed) && !existingValues.contains(typed)) typed else null
            }
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

private fun numberToLong(value: Any?): Long? = when (value) {
    is Byte -> value.toLong()
    is Short -> value.toLong()
    is Int -> value.toLong()
    is Long -> value
    is Float -> value.toLong()
    is Double -> value.toLong()
    is UByte -> value.toLong()
    is UShort -> value.toLong()
    is UInt -> value.toLong()
    is ULong -> if (value > Long.MAX_VALUE.toULong()) Long.MAX_VALUE else value.toLong()
    is Number -> value.toLong()
    else -> null
}

private const val MAX_STRING_CANDIDATE_ATTEMPTS = 10_000
private const val MAX_NUMBER_CANDIDATE_ATTEMPTS = 10_000
private const val MAX_BINARY_CANDIDATE_ATTEMPTS = 10_000

private fun nextStringCandidate(definition: StringDefinition, index: Int): String {
    val minSize = definition.minSize?.toInt() ?: 0
    val base = stringTokenByIndex(index, minSize)
    return if (base.length < minSize) {
        base + "x".repeat(minSize - base.length)
    } else {
        base
    }
}

private fun stringTokenByIndex(index: Int, minSize: Int): String {
    val adjustedIndex = if (minSize > 0) index + 1 else index
    return when (adjustedIndex) {
        0 -> ""
        1 -> "new"
        2 -> "new1"
        3 -> "newa"
        else -> {
            val offset = adjustedIndex - 4
            val tokenNumber = offset / 3 + 1
            when (offset % 3) {
                0 -> indexToAlphaToken(tokenNumber)
                1 -> tokenNumber.toString()
                else -> "new${indexToAlphaToken(tokenNumber)}"
            }
        }
    }
}

private fun indexToAlphaToken(index: Int): String {
    var value = index
    val chars = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        chars.append(('a'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return chars.reverse().toString()
}

private fun isValidDefinitionValue(definition: IsSubDefinition<*, *>, value: Any): Boolean {
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        (definition as? IsPropertyDefinition<Any>)?.validateWithRef(null, value) { null }
    }.isSuccess
}

@Suppress("UNCHECKED_CAST")
private fun nextUniqueNumberCandidate(
    definition: NumberDefinition<*>,
    existingValues: Collection<Any>,
): Any? {
    val propertyDefinition = definition as IsPropertyDefinition<Any>

    val preferredCandidates = listOfNotNull(
        definition.minValue,
        runCatching { definition.fromString("0") }.getOrNull(),
        definition.maxValue,
    )
    for (candidate in preferredCandidates) {
        if (existingValues.contains(candidate)) continue
        val valid = runCatching {
            propertyDefinition.validateWithRef(null, candidate) { null }
        }.isSuccess
        if (valid) return candidate
    }

    if (definition.type == UInt64) {
        return nextUniqueUInt64Candidate(definition, existingValues, propertyDefinition)
    }

    val minBound = numberToLong(definition.minValue)
    val maxBound = numberToLong(definition.maxValue)
    val isFloatingNumber = definition.type == Float32 || definition.type == Float64
    val maxExisting = if (isFloatingNumber) null else existingValues.mapNotNull(::numberToLong).maxOrNull()
    val initial = when {
        maxExisting != null && minBound != null -> maxOf(maxExisting + 1L, minBound)
        maxExisting != null -> maxExisting + 1L
        minBound != null -> minBound
        else -> 0L
    }
    var number = initial
    var attempts = 0
    while (true) {
        if (isFloatingNumber && attempts >= MAX_NUMBER_CANDIDATE_ATTEMPTS) {
            return nextUniqueFractionalCandidate(definition, existingValues, propertyDefinition)
        }
        if (maxBound != null && number > maxBound) {
            return if (isFloatingNumber) {
                nextUniqueFractionalCandidate(definition, existingValues, propertyDefinition)
            } else {
                null
            }
        }
        val candidate = runCatching { definition.fromString(number.toString()) }.getOrNull() ?: return if (isFloatingNumber) {
            nextUniqueFractionalCandidate(definition, existingValues, propertyDefinition)
        } else {
            null
        }
        val valid = runCatching {
            propertyDefinition.validateWithRef(null, candidate) { null }
        }.isSuccess
        if (valid && !existingValues.contains(candidate)) return candidate
        if (number == Long.MAX_VALUE) return null
        number++
        attempts++
    }
}

private fun nextUniqueUInt64Candidate(
    definition: NumberDefinition<*>,
    existingValues: Collection<Any>,
    propertyDefinition: IsPropertyDefinition<Any>,
): Any? {
    val minBound = definition.minValue as? ULong
    val maxBound = definition.maxValue as? ULong
    if (minBound != null && maxBound != null && maxBound < minBound) return null

    var candidate = minBound ?: 0u

    while (true) {
        if (maxBound != null && candidate > maxBound) return null
        val parsed = runCatching { definition.fromString(candidate.toString()) }.getOrNull()
        if (parsed != null && !existingValues.contains(parsed)) {
            val valid = runCatching {
                propertyDefinition.validateWithRef(null, parsed) { null }
            }.isSuccess
            if (valid) return parsed
        }
        candidate = nextULong(candidate) ?: return null
    }
}

private fun nextULong(value: ULong): ULong? = if (value == ULong.MAX_VALUE) null else value + 1u

@Suppress("UNCHECKED_CAST")
private fun nextUniqueFixedBytesCandidate(
    definition: FixedBytesDefinition,
    existingValues: Collection<Any>,
): Any? {
    val propertyDefinition = definition as IsPropertyDefinition<Any>
    val baseBytes = definition.minValue?.bytes ?: ByteArray(definition.byteSize)
    return nextUniqueBinaryCandidate(
        baseBytes = baseBytes,
        existingValues = existingValues,
        toValue = { definition.fromNativeType(it) },
        propertyDefinition = propertyDefinition,
    )
}

@Suppress("UNCHECKED_CAST")
private fun nextUniqueFlexBytesCandidate(
    definition: FlexBytesDefinition,
    existingValues: Collection<Any>,
): Any? {
    val propertyDefinition = definition as IsPropertyDefinition<Any>
    val emptyCandidate = definition.fromString("")
    val emptyValid = runCatching {
        propertyDefinition.validateWithRef(null, emptyCandidate) { null }
    }.isSuccess
    if (emptyValid && !existingValues.contains(emptyCandidate)) return emptyCandidate

    val minValue = definition.minValue
    val minSize = definition.minSize?.toInt() ?: 0
    val maxSize = definition.maxSize?.toInt()
    val baseSize = minValue?.size ?: when {
        minSize > 0 -> minSize
        maxSize == 0 -> 0
        else -> 1
    }
    if (maxSize != null && baseSize > maxSize) return null
    val baseBytes = minValue?.bytes ?: ByteArray(baseSize)
    return nextUniqueBinaryCandidate(
        baseBytes = baseBytes,
        existingValues = existingValues,
        toValue = { definition.fromNativeType(it) },
        propertyDefinition = propertyDefinition,
    )
}

@Suppress("UNCHECKED_CAST")
private fun nextUniqueReferenceCandidate(
    definition: IsReferenceDefinition<*, *>,
    existingValues: Collection<Any>,
): Any? {
    val propertyDefinition = definition as IsPropertyDefinition<Any>
    val baseBytes = ByteArray(definition.byteSize)
    return nextUniqueBinaryCandidate(
        baseBytes = baseBytes,
        existingValues = existingValues,
        toValue = { definition.fromNativeType(it) },
        propertyDefinition = propertyDefinition,
    )
}

private fun nextUniqueBinaryCandidate(
    baseBytes: ByteArray,
    existingValues: Collection<Any>,
    toValue: (ByteArray) -> Any?,
    propertyDefinition: IsPropertyDefinition<Any>,
): Any? {
    for (offset in 0 until MAX_BINARY_CANDIDATE_ATTEMPTS) {
        val candidateBytes = addOffsetToBytes(baseBytes, offset) ?: break
        val candidate = toValue(candidateBytes) ?: continue
        if (existingValues.contains(candidate)) continue
        val valid = runCatching {
            propertyDefinition.validateWithRef(null, candidate) { null }
        }.isSuccess
        if (valid) return candidate
    }
    return null
}

private fun addOffsetToBytes(base: ByteArray, offset: Int): ByteArray? {
    if (offset < 0) return null
    val result = base.copyOf()
    var carry = offset
    var index = result.lastIndex
    while (carry > 0 && index >= 0) {
        val sum = (result[index].toInt() and 0xff) + (carry and 0xff)
        result[index] = (sum and 0xff).toByte()
        carry = (carry ushr 8) + (sum ushr 8)
        index--
    }
    return if (carry == 0) result else null
}

private fun nextUniqueFractionalCandidate(
    definition: NumberDefinition<*>,
    existingValues: Collection<Any>,
    propertyDefinition: IsPropertyDefinition<Any>,
): Any? {
    val min = numberToDouble(definition.minValue)
    val max = numberToDouble(definition.maxValue)
    if (min != null && max != null && max < min) return null

    fun tryCandidate(value: Double): Any? {
        if (!value.isFinite()) return null
        val candidate = runCatching { definition.fromString(value.toString()) }.getOrNull() ?: return null
        if (existingValues.contains(candidate)) return null
        val valid = runCatching { propertyDefinition.validateWithRef(null, candidate) { null } }.isSuccess
        return if (valid) candidate else null
    }

    val lowerBound = min
    val upperBound = max
    if (lowerBound != null && upperBound != null) {
        val minValue: Double = lowerBound
        val maxValue: Double = upperBound
        if (maxValue == minValue) return null
        val inRangeExisting = existingValues.mapNotNull(::numberToDouble)
            .filter { it > minValue && it < maxValue }
            .sorted()
        var lower = minValue
        for (upper in inRangeExisting + maxValue) {
            if (upper <= lower) continue
            val gap = upper - lower
            val mid = lower + gap / 2.0
            tryCandidate(mid)?.let { return it }
            tryCandidate(lower + gap / 4.0)?.let { return it }
            tryCandidate(upper - gap / 4.0)?.let { return it }
            lower = upper
        }
        return null
    }

    if (min != null) {
        var candidate = min + 0.5
        repeat(MAX_NUMBER_CANDIDATE_ATTEMPTS) {
            tryCandidate(candidate)?.let { return it }
            candidate += 0.5
        }
        return null
    }

    if (max != null) {
        var candidate = max - 0.5
        repeat(MAX_NUMBER_CANDIDATE_ATTEMPTS) {
            tryCandidate(candidate)?.let { return it }
            candidate -= 0.5
        }
        return null
    }

    var offset = 0.5
    repeat(MAX_NUMBER_CANDIDATE_ATTEMPTS) {
        tryCandidate(offset)?.let { return it }
        tryCandidate(-offset)?.let { return it }
        offset += 0.5
    }
    return null
}

private fun numberToDouble(value: Any?): Double? = when (value) {
    is Byte -> value.toDouble()
    is Short -> value.toDouble()
    is Int -> value.toDouble()
    is Long -> value.toDouble()
    is Float -> value.toDouble()
    is Double -> value
    is UByte -> value.toDouble()
    is UShort -> value.toDouble()
    is UInt -> value.toDouble()
    is ULong -> value.toDouble()
    is Number -> value.toDouble()
    else -> null
}
