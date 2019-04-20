package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType
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
    override val fillWithNow: Boolean = false,
    override val precision: TimePrecision = TimePrecision.SECONDS
) :
    IsTimeDefinition<Time>,
    IsSerializableFixedBytesEncodable<Time, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Time>,
    HasDefaultValueDefinition<Time> {
    override val propertyDefinitionType = PropertyDefinitionType.Time
    override val wireType = WireType.VAR_INT
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        Time.fromByteReader(length, reader)

    override fun writeStorageBytes(value: Time, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) =
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

    object Model :
        ContextualDataModel<TimeDefinition, ObjectPropertyDefinitions<TimeDefinition>, ContainsDefinitionsContext, TimeDefinitionContext>(
            contextTransformer = { TimeDefinitionContext() },
            properties = object : ObjectPropertyDefinitions<TimeDefinition>() {
                init {
                    IsPropertyDefinition.addRequired(this, TimeDefinition::required)
                    IsPropertyDefinition.addFinal(this, TimeDefinition::final)
                    IsComparableDefinition.addUnique(this, TimeDefinition::unique)
                    IsTimeDefinition.addPrecision(4u, this,
                        TimeDefinition::precision,
                        capturer = { context: TimePrecisionContext, timePrecision ->
                            context.precision = timePrecision
                        }
                    )
                    add(5u, "minValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: TimeDefinitionContext? ->
                                context?.timeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        TimeDefinition::minValue
                    )
                    add(6u, "maxValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: TimeDefinitionContext? ->
                                context?.timeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        TimeDefinition::maxValue
                    )
                    add(7u, "default",
                        ContextualValueDefinition(
                            contextualResolver = { context: TimeDefinitionContext? ->
                                context?.timeDefinition ?: throw ContextNotFoundException()
                            }
                        ),
                        TimeDefinition::default
                    )
                    IsMomentDefinition.addFillWithNow(8u, this, TimeDefinition::fillWithNow)
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<TimeDefinition>) = TimeDefinition(
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

class TimeDefinitionContext : TimePrecisionContext() {
    private var _timeDefinition: Lazy<TimeDefinition> = lazy {
        TimeDefinition(
            precision = precision ?: throw ContextNotFoundException()
        )
    }

    val timeDefinition: TimeDefinition get() = this._timeDefinition.value
}
