package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.DataModelContext
import maryk.lib.time.Time

/** Definition for Time properties */
data class TimeDefinition(
    override val indexed: Boolean = false,
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
    HasDefaultValueDefinition<Time>
{
    override val propertyDefinitionType = PropertyDefinitionType.Time
    override val wireType = WireType.VAR_INT
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        Time.fromByteReader(length, reader)

    override fun writeStorageBytes(value: Time, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) = when(this.precision) {
        TimePrecision.SECONDS -> Time.ofSecondOfDay(initIntByVar(reader))
        TimePrecision.MILLIS -> Time.ofMilliOfDay(initIntByVar(reader))
    }

    override fun calculateTransportByteLength(value: Time) = when(this.precision) {
        TimePrecision.SECONDS -> value.toSecondsOfDay().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toMillisOfDay().calculateVarByteLength()
    }

    override fun writeTransportBytes(value: Time, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) {
        val toEncode = when(this.precision) {
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

    object Model : ContextualDataModel<TimeDefinition, PropertyDefinitions<TimeDefinition>, DataModelContext, TimeDefinitionContext>(
        contextTransformer = { TimeDefinitionContext() },
        properties = object : PropertyDefinitions<TimeDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, TimeDefinition::indexed)
                IsPropertyDefinition.addRequired(this, TimeDefinition::required)
                IsPropertyDefinition.addFinal(this, TimeDefinition::final)
                IsComparableDefinition.addUnique(this, TimeDefinition::unique)
                IsTimeDefinition.addPrecision(4,this,
                    TimeDefinition::precision,
                    capturer = { context: TimePrecisionContext, timePrecision ->
                        context.precision = timePrecision
                    }
                )
                add(5, "minValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    ),
                    TimeDefinition::minValue
                )
                add(6, "maxValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    ),
                    TimeDefinition::maxValue
                )
                add(7, "default",
                    ContextualValueDefinition(
                        contextualResolver = { context: TimeDefinitionContext? ->
                            context?.timeDefinition ?: throw ContextNotFoundException()
                        }
                    ),
                    TimeDefinition::default
                )
                IsMomentDefinition.addFillWithNow(8, this, TimeDefinition::fillWithNow)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = TimeDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            precision = map(4),
            minValue = map(5),
            maxValue = map(6),
            default = map(7),
            fillWithNow = map(8)
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
