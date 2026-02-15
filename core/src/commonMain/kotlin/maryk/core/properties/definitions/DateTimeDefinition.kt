package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.byteSize
import maryk.core.properties.types.fromByteReader
import maryk.core.properties.types.writeBytes
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.yaml.YamlWriter
import maryk.lib.exceptions.ParseException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Definition for DateTime properties
 */
@OptIn(ExperimentalTime::class)
data class DateTimeDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val precision: TimePrecision = TimePrecision.SECONDS,
    override val minValue: LocalDateTime? = null,
    override val maxValue: LocalDateTime? = null,
    override val default: LocalDateTime? = null
) :
    IsTimeDefinition<LocalDateTime>,
    IsTransportablePropertyDefinitionType<LocalDateTime>,
    HasDefaultValueDefinition<LocalDateTime> {
    override val propertyDefinitionType = PropertyDefinitionType.DateTime
    override val wireType = VAR_INT
    override val byteSize = DateTimeDefinition.byteSize(precision)

    override fun readStorageBytes(length: Int, reader: () -> Byte) = LocalDateTime.fromByteReader(length, reader)

    override fun writeStorageBytes(value: LocalDateTime, writer: (byte: Byte) -> Unit) = value.writeBytes(precision, writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: LocalDateTime?
    ): LocalDateTime =
        when (this.precision) {
            TimePrecision.SECONDS -> Instant.fromEpochSeconds(initLongByVar(reader))
            TimePrecision.MILLIS -> Instant.fromEpochMilliseconds(initLongByVar(reader))
            TimePrecision.NANOS -> Instant.fromEpochSeconds(initLongByVar(reader), initIntByVar(reader))
        }.toLocalDateTime(UTC)

    override fun calculateTransportByteLength(value: LocalDateTime): Int {
        val utcValue = value.toInstant(UTC)
        return when (this.precision) {
            TimePrecision.SECONDS -> utcValue.epochSeconds.calculateVarByteLength()
            TimePrecision.MILLIS -> utcValue.toEpochMilliseconds().calculateVarByteLength()
            TimePrecision.NANOS -> utcValue.epochSeconds.calculateVarByteLength() + utcValue.nanosecondsOfSecond.calculateVarByteLength()
        }
    }

    override fun writeTransportBytes(
        value: LocalDateTime,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        val utcValue = value.toInstant(UTC)
        when (this.precision) {
            TimePrecision.SECONDS -> utcValue.epochSeconds.writeVarBytes(writer)
            TimePrecision.MILLIS -> utcValue.toEpochMilliseconds().writeVarBytes(writer)
            TimePrecision.NANOS -> {
                utcValue.epochSeconds.writeVarBytes(writer)
                utcValue.nanosecondsOfSecond.writeVarBytes(writer)
            }
        }
    }

    override fun fromString(string: String) = try {
        LocalDateTime.parse(string)
    } catch (e: IllegalArgumentException) {
        throw ParseException(string, e)
    }

    override fun fromNativeType(value: Any) = value as? LocalDateTime

    override fun writeJsonValue(value: LocalDateTime, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        val stringValue = this.asString(value, context)
        if (writer is YamlWriter) {
            writer.writeValue(stringValue)
        } else {
            writer.writeString(stringValue)
        }
    }

    object Model : ContextualDataModel<DateTimeDefinition, Model, ContainsDefinitionsContext, DateTimeDefinitionContext>(
        contextTransformer = { DateTimeDefinitionContext() },
    ) {
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

        override fun invoke(values: ObjectValues<DateTimeDefinition, Model>) = DateTimeDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            precision = values(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u)
        )
    }

    companion object {
        val MIN = DateDefinition.MIN.atTime(0, 0)
        val MAX_IN_SECONDS = DateDefinition.MAX.atTime(23, 59, 59)
        val MAX_IN_MILLIS = DateDefinition.MAX.atTime(23, 59, 59, 999000000)
        val MAX_IN_NANOS = DateDefinition.MAX.atTime(23, 59, 59, 999_999_999)

        fun nowUTC() = Clock.System.now().toLocalDateTime(UTC)
    }
}

class DateTimeDefinitionContext : TimePrecisionContext() {
    val dateTimeDefinition by lazy {
        DateTimeDefinition(
            precision = precision ?: throw ContextNotFoundException()
        )
    }
}

fun IsValuesDataModel.dateTime(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: LocalDateTime? = null,
    maxValue: LocalDateTime? = null,
    default: LocalDateTime? = null,
    alternativeNames: Set<String>? = null,
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<LocalDateTime, LocalDateTime, IsPropertyContext, DateTimeDefinition, Any>(
        index,
        name ?: propName,
        DateTimeDefinition(required, final, unique, precision, minValue, maxValue, default),
        alternativeNames,
        sensitive,
    )
}

fun <TO: Any, DO: Any> IsObjectDataModel<DO>.dateTime(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: LocalDateTime? = null,
    maxValue: LocalDateTime? = null,
    default: LocalDateTime? = null,
    alternativeNames: Set<String>? = null,
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<LocalDateTime, TO, IsPropertyContext, DateTimeDefinition, DO>, DO, IsPropertyContext> =
    dateTime(index, getter, name, required, final, sensitive,  unique, precision, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.dateTime(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    precision: TimePrecision = TimePrecision.SECONDS,
    minValue: LocalDateTime? = null,
    maxValue: LocalDateTime? = null,
    default: LocalDateTime? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> LocalDateTime?)? = null,
    fromSerializable: ((LocalDateTime?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, LocalDateTime) -> Unit)? = null,
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DateTimeDefinition(required, final, unique, precision, minValue, maxValue, default),
        alternativeNames,
        sensitive,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
