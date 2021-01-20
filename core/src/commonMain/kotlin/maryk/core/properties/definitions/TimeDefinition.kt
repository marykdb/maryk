package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
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
import maryk.lib.time.Time

/** Definition for Time properties */
data class TimeDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Time? = null,
    override val maxValue: Time? = null,
    override val default: Time? = null,
    override val precision: TimePrecision = TimePrecision.SECONDS
) :
    IsTimeDefinition<Time>,
    IsSerializableFixedBytesEncodable<Time, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Time>,
    HasDefaultValueDefinition<Time> {
    override val propertyDefinitionType = PropertyDefinitionType.Time
    override val wireType = VAR_INT
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        Time.fromByteReader(length, reader)

    override fun writeStorageBytes(value: Time, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: Time?
    ) =
        when (this.precision) {
            TimePrecision.SECONDS -> Time.ofSecondOfDay(initIntByVar(reader))
            TimePrecision.MILLIS -> Time.ofMilliOfDay(initIntByVar(reader))
        }

    override fun calculateTransportByteLength(value: Time) = when (this.precision) {
        TimePrecision.SECONDS -> value.toSecondsOfDay().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toMillisOfDay().calculateVarByteLength()
    }

    override fun writeTransportBytes(
        value: Time,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val toEncode = when (this.precision) {
            TimePrecision.SECONDS -> value.toSecondsOfDay()
            TimePrecision.MILLIS -> value.toMillisOfDay()
        }
        toEncode.writeVarBytes(writer)
    }

    override fun fromString(string: String) = Time.parse(string)

    override fun fromNativeType(value: Any) = when (value) {
        is Long -> Time.ofSecondOfDay(value.toInt())
        is Int -> Time.ofSecondOfDay(value)
        else -> value as? Time
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
}

class TimeDefinitionContext : TimePrecisionContext() {
    val timeDefinition by lazy {
        TimeDefinition(
            precision = precision ?: throw ContextNotFoundException()
        )
    }
}

fun PropertyDefinitions.time(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Time? = null,
    maxValue: Time? = null,
    default: Time? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<Time, Time, IsPropertyContext, TimeDefinition, Any>(
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
    minValue: Time? = null,
    maxValue: Time? = null,
    default: Time? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<Time, TO, IsPropertyContext, TimeDefinition, DO>, DO, IsPropertyContext> =
    time(index, getter, name, required, final,  unique, minValue, maxValue, default, precision, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.time(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Time? = null,
    maxValue: Time? = null,
    default: Time? = null,
    precision: TimePrecision = TimePrecision.SECONDS,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> Time?)? = null,
    fromSerializable: (Unit.(Time?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, Time) -> Unit)? = null
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
