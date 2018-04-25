package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.lib.time.Time

/** Definition for Time properties */
data class TimeDefinition(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Time? = null,
    override val maxValue: Time? = null,
    override val fillWithNow: Boolean = false,
    override val precision: TimePrecision = TimePrecision.SECONDS
) :
    IsTimeDefinition<Time>,
    IsSerializableFixedBytesEncodable<Time, IsPropertyContext>,
    IsTransportablePropertyDefinitionType
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

    internal object Model : SimpleDataModel<TimeDefinition, PropertyDefinitions<TimeDefinition>>(
        properties = object : PropertyDefinitions<TimeDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, TimeDefinition::indexed)
                IsPropertyDefinition.addSearchable(this, TimeDefinition::searchable)
                IsPropertyDefinition.addRequired(this, TimeDefinition::required)
                IsPropertyDefinition.addFinal(this, TimeDefinition::final)
                IsComparableDefinition.addUnique(this, TimeDefinition::unique)
                add(5, "minValue", TimeDefinition(precision = TimePrecision.MILLIS), TimeDefinition::minValue)
                add(6, "maxValue", TimeDefinition(precision = TimePrecision.MILLIS), TimeDefinition::maxValue)
                IsMomentDefinition.addFillWithNow(this, TimeDefinition::fillWithNow)
                IsTimeDefinition.addPrecision(this, TimeDefinition::precision)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = TimeDefinition(
            indexed = map[0] as Boolean? ?: false,
            searchable = map[1] as Boolean? ?: true,
            required = map[2] as Boolean? ?: true,
            final = map[3] as Boolean? ?: false,
            unique = map[4] as Boolean? ?: false,
            minValue = map[5] as Time?,
            maxValue = map[6] as Time?,
            fillWithNow = map[7] as Boolean? ?: false,
            precision = map[8] as? TimePrecision ?: TimePrecision.SECONDS
        )
    }
}
