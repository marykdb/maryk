package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
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

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: Date?
    ) =
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

    @Suppress("unused")
    object Model : SimpleObjectDataModel<DateDefinition, ObjectPropertyDefinitions<DateDefinition>>(
        properties = object : ObjectPropertyDefinitions<DateDefinition>() {
            val required by boolean(1u, DateDefinition::required, default = true)
            val final by boolean(2u, DateDefinition::final, default = false)
            val unique by boolean(3u, DateDefinition::unique, default = false)
            val minValue by date(4u, DateDefinition::minValue)
            val maxValue by date(5u, DateDefinition::maxValue)
            val default by date(6u, DateDefinition::default)
            val fillWithNow by boolean(7u, DateDefinition::fillWithNow, default = false)
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


fun PropertyDefinitions.date(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Date? = null,
    maxValue: Date? = null,
    default: Date? = null,
    fillWithNow: Boolean = false,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<Date, Date, IsPropertyContext, DateDefinition, Any>(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default, fillWithNow),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.date(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Date? = null,
    maxValue: Date? = null,
    default: Date? = null,
    fillWithNow: Boolean = false,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<Date, TO, IsPropertyContext, DateDefinition, DO>, DO> =
    date(index, getter, name, required, final,  unique, minValue, maxValue, default, fillWithNow, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.date(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Date? = null,
    maxValue: Date? = null,
    default: Date? = null,
    fillWithNow: Boolean = false,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> Date?)? = null,
    fromSerializable: (Unit.(Date?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, Date) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default, fillWithNow),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
