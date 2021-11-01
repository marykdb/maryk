package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
import maryk.core.properties.types.localDateFromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.SimpleObjectValues
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Date.ofEpochDay
import maryk.lib.time.epochDay

/** Definition for Date properties */
data class DateDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: LocalDate? = null,
    override val maxValue: LocalDate? = null,
    override val default: LocalDate? = null
) :
    IsComparableDefinition<LocalDate, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<LocalDate, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<LocalDate>,
    HasDefaultValueDefinition<LocalDate> {
    override val propertyDefinitionType = PropertyDefinitionType.Date
    override val wireType = VAR_INT
    override val byteSize = 4

    override fun readStorageBytes(length: Int, reader: () -> Byte): LocalDate = localDateFromByteReader(reader)

    override fun calculateStorageByteLength(value: LocalDate) = this.byteSize

    override fun writeStorageBytes(value: LocalDate, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: LocalDate?
    ): LocalDate =
        ofEpochDay(initIntByVar(reader).decodeZigZag())

    override fun calculateTransportByteLengthWithKey(index: UInt, value: LocalDate, cacher: WriteCacheWriter): Int {
        return super<IsSerializableFixedBytesEncodable>.calculateTransportByteLengthWithKey(index, value, cacher)
    }

    override fun calculateTransportByteLength(value: LocalDate) = value.epochDay.encodeZigZag().calculateVarByteLength()

    override fun writeTransportBytes(
        value: LocalDate,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val epochDay = value.epochDay
        epochDay.encodeZigZag().writeVarBytes(writer)
    }

    override fun asString(value: LocalDate): String {
        return super.asString(value)
    }

    override fun fromString(string: String) = try {
        LocalDate.parse(string)
    } catch (e: IllegalArgumentException) {
        throw ParseException(string, e)
    }

    override fun fromNativeType(value: Any): LocalDate? {
        return when {
            value is LocalDate -> value
            value is LocalDateTime && value.hour == 0 && value.minute == 0 && value.second == 0 && value.nanosecond == 0 -> value.date
            else -> null
        }
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
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DateDefinition>) = DateDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u)
        )
    }
}


fun PropertyDefinitions.date(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<LocalDate, LocalDate, IsPropertyContext, DateDefinition, Any>(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default),
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
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<LocalDate, TO, IsPropertyContext, DateDefinition, DO>, DO, IsPropertyContext> =
    date(index, getter, name, required, final,  unique, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.date(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> LocalDate?)? = null,
    fromSerializable: (Unit.(LocalDate?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, LocalDate) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
