package maryk.core.properties.definitions

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.lib.exceptions.ParseException

/** Definition for Time properties */
data class TimeDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: LocalTime? = null,
    override val maxValue: LocalTime? = null,
    override val default: LocalTime? = null,
    override val precision: TimePrecision = TimePrecision.SECONDS
) :
    IsTimeDefinition<LocalTime>,
    IsTransportablePropertyDefinitionType<LocalTime>,
    HasDefaultValueDefinition<LocalTime> {
    override val propertyDefinitionType = PropertyDefinitionType.Time
    override val wireType = VAR_INT
    override val byteSize = TimeDefinition.byteSize(precision)

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        LocalTime.fromByteReader(length, reader)

    override fun writeStorageBytes(value: LocalTime, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: LocalTime?
    ) =
        when (this.precision) {
            TimePrecision.SECONDS -> LocalTime.fromSecondOfDay(initIntByVar(reader))
            TimePrecision.MILLIS -> LocalTime.fromMillisecondOfDay(initIntByVar(reader))
            TimePrecision.NANOS -> LocalTime.fromNanosecondOfDay(initLongByVar(reader))
        }

    override fun calculateTransportByteLength(value: LocalTime) = when (this.precision) {
        TimePrecision.SECONDS -> value.toSecondOfDay().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toMillisecondOfDay().calculateVarByteLength()
        TimePrecision.NANOS -> value.toNanosecondOfDay().calculateVarByteLength()
    }

    override fun writeTransportBytes(
        value: LocalTime,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        when (this.precision) {
            TimePrecision.SECONDS -> value.toSecondOfDay().writeVarBytes(writer)
            TimePrecision.MILLIS -> value.toMillisecondOfDay().writeVarBytes(writer)
            TimePrecision.NANOS -> value.toNanosecondOfDay().writeVarBytes(writer)
        }
    }

    override fun fromString(string: String) = try {
        LocalTime.parse(string)
    } catch (e: Exception) {
        throw ParseException(e.message ?: "Issue with parsing time: $string")
    }

    override fun fromNativeType(value: Any) = when (value) {
        is Long -> LocalTime.fromSecondOfDay(value.toInt())
        is Int -> LocalTime.fromSecondOfDay(value)
        else -> value as? LocalTime
    }

    @Suppress("unused")
    object Model :
        ContextualDataModel<TimeDefinition, ObjectPropertyDefinitions<TimeDefinition>, ContainsDefinitionsContext, TimeDefinitionContext>(
            contextTransformer = { TimeDefinitionContext() },
            properties = object : ObjectPropertyDefinitions<TimeDefinition>() {
                val required by boolean(1u, TimeDefinition::required, default = true)
                val final by boolean(2u, TimeDefinition::final, default = false)
                val unique by boolean(3u, TimeDefinition::unique, default = false)
                val precision by enum(4u,
                    TimeDefinition::precision,
                    enum = TimePrecision,
                    default = TimePrecision.SECONDS,
                    capturer = { context: TimePrecisionContext, timePrecision ->
                        context.precision = timePrecision
                    }
                )
                val minValue by contextual(
                    index = 5u,
                    getter = TimeDefinition::minValue,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
                val maxValue by contextual(
                    index = 6u,
                    getter = TimeDefinition::maxValue,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
                val default by contextual(
                    index = 7u,
                    getter = TimeDefinition::default,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
            }
        ) {
        override fun invoke(values: SimpleObjectValues<TimeDefinition>) = TimeDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            precision = values(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u)
        )
    }

    companion object {
        val MIN = LocalTime(0, 0)
        val MAX_IN_SECONDS = LocalTime(23, 59, 59)
        val MAX_IN_MILLIS = LocalTime(23, 59, 59, 999_000_000)
        val MAX_IN_NANOS = LocalTime(23, 59, 59, 999_999_999)

        fun nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC).time
    }
}

class TimeDefinitionContext : TimePrecisionContext() {
    val timeDefinition by lazy {
        TimeDefinition(
            precision = precision ?: throw ContextNotFoundException()
        )
    }
}

fun IsValuesPropertyDefinitions.time(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: LocalTime? = null,
    maxValue: LocalTime? = null,
    default: LocalTime? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<LocalTime, LocalTime, IsPropertyContext, TimeDefinition, Any>(
        index,
        name ?: propName,
        TimeDefinition(required, final, unique, minValue, maxValue, default, precision),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.time(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: LocalTime? = null,
    maxValue: LocalTime? = null,
    default: LocalTime? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<LocalTime, TO, IsPropertyContext, TimeDefinition, DO>, DO, IsPropertyContext> =
    time(index, getter, name, required, final,  unique, minValue, maxValue, default, precision, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.time(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: LocalTime? = null,
    maxValue: LocalTime? = null,
    default: LocalTime? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> LocalTime?)? = null,
    fromSerializable: (Unit.(LocalTime?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, LocalTime) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        TimeDefinition(required, final, unique, minValue, maxValue, default, precision),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
