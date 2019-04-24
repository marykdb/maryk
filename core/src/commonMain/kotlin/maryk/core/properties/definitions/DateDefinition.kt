package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.values.SimpleObjectValues
import maryk.lib.time.Time

/** Definition for Date properties */
data class DateDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Date? = null,
    override val maxValue: Date? = null,
    override val default: Date? = null,
    override val fillWithNow: Boolean = false
) :
    IsMomentDefinition<Date>,
    IsSerializableFixedBytesEncodable<Date, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Date>,
    HasDefaultValueDefinition<Date> {
    override val propertyDefinitionType = PropertyDefinitionType.Date
    override val wireType = VAR_INT
    override val byteSize = 4

    override fun createNow() = Date.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Date.fromByteReader(reader)

    override fun calculateStorageByteLength(value: Date) = this.byteSize

    override fun writeStorageBytes(value: Date, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) =
        Date.ofEpochDay(initIntByVar(reader).decodeZigZag())

    override fun calculateTransportByteLength(value: Date) = value.epochDay.encodeZigZag().calculateVarByteLength()

    override fun writeTransportBytes(
        value: Date,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val epochDay = value.epochDay
        epochDay.encodeZigZag().writeVarBytes(writer)
    }

    override fun fromString(string: String) = Date.parse(string)

    override fun fromNativeType(value: Any) = when {
        value is Date -> value
        value is DateTime && value.time == Time.MIN -> value.date
        else -> null
    }

    object Model : SimpleObjectDataModel<DateDefinition, ObjectPropertyDefinitions<DateDefinition>>(
        properties = object : ObjectPropertyDefinitions<DateDefinition>() {
            init {
                IsPropertyDefinition.addRequired(this, DateDefinition::required)
                IsPropertyDefinition.addFinal(this, DateDefinition::final)
                IsComparableDefinition.addUnique(this, DateDefinition::unique)
                add(4u, "minValue", DateDefinition(), DateDefinition::minValue)
                add(5u, "maxValue", DateDefinition(), DateDefinition::maxValue)
                add(6u, "default", DateDefinition(), DateDefinition::default)
                IsMomentDefinition.addFillWithNow(7u, this, DateDefinition::fillWithNow)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DateDefinition>) = DateDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u),
            fillWithNow = values(7u)
        )
    }
}
