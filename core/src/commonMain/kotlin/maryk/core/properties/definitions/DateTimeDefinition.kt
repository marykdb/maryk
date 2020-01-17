package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
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
    override val default: DateTime? = null,
    override val fillWithNow: Boolean = false
) :
    IsTimeDefinition<DateTime>,
    IsSerializableFixedBytesEncodable<DateTime, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<DateTime>,
    HasDefaultValueDefinition<DateTime>,
    IsWrappableDefinition<DateTime, IsPropertyContext, FixedBytesDefinitionWrapper<DateTime, DateTime, IsPropertyContext, DateTimeDefinition, Any>> {
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

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        FixedBytesDefinitionWrapper<DateTime, DateTime, IsPropertyContext, DateTimeDefinition, Any>(index, name, this, alternativeNames)

    object Model :
        ContextualDataModel<DateTimeDefinition, ObjectPropertyDefinitions<DateTimeDefinition>, ContainsDefinitionsContext, DateTimeDefinitionContext>(
            contextTransformer = { DateTimeDefinitionContext() },
            properties = object : ObjectPropertyDefinitions<DateTimeDefinition>() {
                init {
                    IsPropertyDefinition.addRequired(this, DateTimeDefinition::required)
                    IsPropertyDefinition.addFinal(this, DateTimeDefinition::final)
                    IsComparableDefinition.addUnique(this, DateTimeDefinition::unique)
                    IsTimeDefinition.addPrecision(4u, this,
                        DateTimeDefinition::precision,
                        capturer = { context: TimePrecisionContext, timePrecision ->
                            context.precision = timePrecision
                        }
                    )
                    add(5u, "minValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: DateTimeDefinitionContext? ->
                                context?.dateTimeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        DateTimeDefinition::minValue
                    )
                    add(6u, "maxValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: DateTimeDefinitionContext? ->
                                context?.dateTimeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        DateTimeDefinition::maxValue
                    )
                    add(7u, "default",
                        ContextualValueDefinition(
                            contextualResolver = { context: DateTimeDefinitionContext? ->
                                context?.dateTimeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        DateTimeDefinition::default
                    )
                    IsMomentDefinition.addFillWithNow(8u, this, DateTimeDefinition::fillWithNow)
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<DateTimeDefinition>) = DateTimeDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            precision = values(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u),
            fillWithNow = values(8u)
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
