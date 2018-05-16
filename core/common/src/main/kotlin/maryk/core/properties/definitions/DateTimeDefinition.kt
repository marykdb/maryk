package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader

/**
 * Definition for DateTime properties
 */
data class DateTimeDefinition(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
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
    IsWithDefaultDefinition<DateTime>
{
    override val propertyDefinitionType = PropertyDefinitionType.DateTime
    override val wireType = WireType.VAR_INT
    override val byteSize = DateTime.byteSize(precision)

    override fun createNow() = DateTime.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = DateTime.fromByteReader(length, reader)

    override fun writeStorageBytes(value: DateTime, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) = when(this.precision) {
        TimePrecision.SECONDS -> DateTime.ofEpochSecond(initLongByVar(reader))
        TimePrecision.MILLIS -> DateTime.ofEpochMilli(initLongByVar(reader))
    }

    override fun calculateTransportByteLength(value: DateTime) = when(this.precision) {
        TimePrecision.SECONDS -> value.toEpochSecond().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toEpochMilli().calculateVarByteLength()
    }

    override fun writeTransportBytes(value: DateTime, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) {
        val epochUnit = when(this.precision) {
            TimePrecision.SECONDS -> value.toEpochSecond()
            TimePrecision.MILLIS -> value.toEpochMilli()
        }
        epochUnit.writeVarBytes(writer)
    }

    override fun fromString(string: String) = DateTime.parse(string)

    override fun fromNativeType(value: Any) = value as? DateTime

    object Model : SimpleDataModel<DateTimeDefinition, PropertyDefinitions<DateTimeDefinition>>(
        properties = object : PropertyDefinitions<DateTimeDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, DateTimeDefinition::indexed)
                IsPropertyDefinition.addSearchable(this, DateTimeDefinition::searchable)
                IsPropertyDefinition.addRequired(this, DateTimeDefinition::required)
                IsPropertyDefinition.addFinal(this, DateTimeDefinition::final)
                IsComparableDefinition.addUnique(this, DateTimeDefinition::unique)
                IsTimeDefinition.addPrecision(5, this, DateTimeDefinition::precision)
                add(6, "minValue", DateTimeDefinition(precision = TimePrecision.MILLIS), DateTimeDefinition::minValue)
                add(7, "maxValue", DateTimeDefinition(precision = TimePrecision.MILLIS), DateTimeDefinition::maxValue)
                add(8, "default", DateTimeDefinition(precision = TimePrecision.MILLIS), DateTimeDefinition::default)
                IsMomentDefinition.addFillWithNow(9, this, DateTimeDefinition::fillWithNow)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = DateTimeDefinition(
            indexed = map(0),
            searchable = map(1),
            required = map(2),
            final = map(3),
            unique = map(4),
            precision = map(5),
            minValue = map(6),
            maxValue = map(7),
            default = map(8),
            fillWithNow = map(9)
        )
    }
}
