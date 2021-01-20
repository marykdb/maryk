package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLongByVar
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
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues

/**
 * Definition for DateTime properties
 */
data class DateTimeDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val precision: TimePrecision = TimePrecision.SECONDS,
    override val minValue: DateTime? = null,
    override val maxValue: DateTime? = null,
    override val default: DateTime? = null
) :
    IsTimeDefinition<DateTime>,
    IsSerializableFixedBytesEncodable<DateTime, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<DateTime>,
    HasDefaultValueDefinition<DateTime> {
    override val propertyDefinitionType = PropertyDefinitionType.DateTime
    override val wireType = VAR_INT
    override val byteSize = DateTime.byteSize(precision)

    override fun createNow() = DateTime.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = DateTime.fromByteReader(length, reader)

    override fun writeStorageBytes(value: DateTime, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: DateTime?
    ) =
        when (this.precision) {
            TimePrecision.SECONDS -> DateTime.ofEpochSecond(initLongByVar(reader))
            TimePrecision.MILLIS -> DateTime.ofEpochMilli(initLongByVar(reader))
        }

    override fun calculateTransportByteLength(value: DateTime) = when (this.precision) {
        TimePrecision.SECONDS -> value.toEpochSecond().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toEpochMilli().calculateVarByteLength()
    }

    override fun writeTransportBytes(
        value: DateTime,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val epochUnit = when (this.precision) {
            TimePrecision.SECONDS -> value.toEpochSecond()
            TimePrecision.MILLIS -> value.toEpochMilli()
        }
        epochUnit.writeVarBytes(writer)
    }

    override fun fromString(string: String) = DateTime.parse(string)

    override fun fromNativeType(value: Any) = value as? DateTime

    @Suppress("unused")
    object Model :
        ContextualDataModel<DateTimeDefinition, ObjectPropertyDefinitions<DateTimeDefinition>, ContainsDefinitionsContext, DateTimeDefinitionContext>(
            contextTransformer = { DateTimeDefinitionContext() },
            properties = object : ObjectPropertyDefinitions<DateTimeDefinition>() {
                val required by boolean(1u, DateTimeDefinition::required, default = true)
                val final by boolean(2u, DateTimeDefinition::final, default = false)
                val unique by boolean(3u, DateTimeDefinition::unique, default = false)
                val precision by enum(4u,
                    DateTimeDefinition::precision,
                    enum = TimePrecision,
                    default = TimePrecision.SECONDS,
                    capturer = { context: TimePrecisionContext, timePrecision ->
                        context.precision = timePrecision
                    }
                )
                val minValue by contextual(
                    index = 5u,
                    getter = DateTimeDefinition::minValue,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: DateTimeDefinitionContext? ->
                            context?.dateTimeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
                val maxValue by contextual(
                    index = 6u,
                    getter = DateTimeDefinition::maxValue,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: DateTimeDefinitionContext? ->
                            context?.dateTimeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
                val default by contextual(
                    index = 7u,
                    getter = DateTimeDefinition::default,
                    definition = ContextualValueDefinition(
                        contextualResolver = { context: DateTimeDefinitionContext? ->
                            context?.dateTimeDefinition ?: throw ContextNotFoundException()
                        }
                    )
                )
            }
        ) {
        override fun invoke(values: SimpleObjectValues<DateTimeDefinition>) = DateTimeDefinition(
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

class DateTimeDefinitionContext : TimePrecisionContext() {
    val dateTimeDefinition by lazy {
        DateTimeDefinition(
            precision = precision ?: throw ContextNotFoundException()
        )
    }
}

fun PropertyDefinitions.dateTime(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: DateTime? = null,
    maxValue: DateTime? = null,
    default: DateTime? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<DateTime, DateTime, IsPropertyContext, DateTimeDefinition, Any>(
        index,
        name ?: propName,
        DateTimeDefinition(required, final, unique, precision, minValue, maxValue, default),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.dateTime(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: DateTime? = null,
    maxValue: DateTime? = null,
    default: DateTime? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<DateTime, TO, IsPropertyContext, DateTimeDefinition, DO>, DO, IsPropertyContext> =
    dateTime(index, getter, name, required, final,  unique, precision, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.dateTime(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: DateTime? = null,
    maxValue: DateTime? = null,
    default: DateTime? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> DateTime?)? = null,
    fromSerializable: (Unit.(DateTime?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, DateTime) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DateTimeDefinition(required, final, unique, precision, minValue, maxValue, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
