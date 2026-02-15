package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.types.localDateFromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.yaml.YamlWriter
import maryk.lib.exceptions.ParseException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
        LocalDate.fromEpochDays(initIntByVar(reader).decodeZigZag())

    override fun calculateTransportByteLength(value: LocalDate) = value.toEpochDays().encodeZigZag().calculateVarByteLength()

    override fun writeTransportBytes(
        value: LocalDate,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val epochDay = value.toEpochDays()
        epochDay.encodeZigZag().writeVarBytes(writer)
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

    override fun writeJsonValue(value: LocalDate, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        val stringValue = this.asString(value, context)
        if (writer is YamlWriter) {
            writer.writeValue(stringValue)
        } else {
            writer.writeString(stringValue)
        }
    }

    object Model : SimpleObjectModel<DateDefinition, IsObjectDataModel<DateDefinition>>() {
        val required by boolean(1u, DateDefinition::required, default = true)
        val final by boolean(2u, DateDefinition::final, default = false)
        val unique by boolean(3u, DateDefinition::unique, default = false)
        val minValue by date(4u, DateDefinition::minValue)
        val maxValue by date(5u, DateDefinition::maxValue)
        val default by date(6u, DateDefinition::default)

        override fun invoke(values: SimpleObjectValues<DateDefinition>) = DateDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u)
        )
    }

    @OptIn(ExperimentalTime::class)
    companion object {
        val MIN = LocalDate(-999_999, 1, 1)
        val MAX = LocalDate(999_999, 12, 31)

        fun nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    }
}

fun IsValuesDataModel.date(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null,
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<LocalDate, LocalDate, IsPropertyContext, DateDefinition, Any>(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default),
        alternativeNames,
        sensitive,
    )
}

fun <TO: Any, DO: Any> IsObjectDataModel<DO>.date(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null,
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<LocalDate, TO, IsPropertyContext, DateDefinition, DO>, DO, IsPropertyContext> =
    date(index, getter, name, required, final, sensitive,  unique, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.date(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: LocalDate? = null,
    maxValue: LocalDate? = null,
    default: LocalDate? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> LocalDate?)? = null,
    fromSerializable: ((LocalDate?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, LocalDate) -> Unit)? = null,
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DateDefinition(required, final, unique, minValue, maxValue, default),
        alternativeNames,
        sensitive,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
