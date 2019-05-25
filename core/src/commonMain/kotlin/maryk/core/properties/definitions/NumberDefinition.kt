package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCacheReader
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException
import kotlin.experimental.xor

/** Definition for Number properties */
data class NumberDefinition<T : Comparable<T>>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: T? = null,
    override val maxValue: T? = null,
    override val default: T? = null,
    override val random: Boolean = false,
    val reversedStorage: Boolean? = null,
    val type: NumberDescriptor<T>
) :
    IsNumericDefinition<T>,
    IsSerializableFixedBytesEncodable<T, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<T>,
    HasDefaultValueDefinition<T> {
    override val propertyDefinitionType = PropertyDefinitionType.Number
    override val wireType = type.wireType
    override val byteSize = type.size

    override fun createRandom() = type.createRandom()

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        when (this.reversedStorage) {
            true -> this.type.fromStorageByteReader(length) {
                MAX_BYTE xor reader()
            }
            else -> this.type.fromStorageByteReader(length, reader)
        }

    override fun calculateStorageByteLength(value: T) = type.size

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) =
        when (this.reversedStorage) {
            true -> this.type.writeStorageBytes(value) {
                writer(MAX_BYTE xor it)
            }
            else -> this.type.writeStorageBytes(value, writer)
        }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: T?
    ) =
        this.type.readTransportBytes(reader)

    override fun calculateTransportByteLength(value: T) = this.type.calculateTransportByteLength(value)

    override fun writeTransportBytes(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) =
        this.type.writeTransportBytes(value, writer)

    override fun fromString(string: String) = try {
        type.ofString(string)
    } catch (e: Throwable) {
        throw ParseException(string, e)
    }

    override fun fromNativeType(value: Any) = fromNativeType(this.type, value)

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: IsPropertyContext?) = when (type) {
        !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
            writer.writeValue(
                this.asString(value)
            )
        }
        else -> super.writeJsonValue(value, writer, context)
    }

    object Model :
        ContextualDataModel<NumberDefinition<*>, ObjectPropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>(
            contextTransformer = { NumericContext() },
            properties = object : ObjectPropertyDefinitions<NumberDefinition<*>>() {
                init {
                    IsPropertyDefinition.addRequired(this, NumberDefinition<*>::required)
                    IsPropertyDefinition.addFinal(this, NumberDefinition<*>::final)
                    IsComparableDefinition.addUnique(this, NumberDefinition<*>::unique)
                    @Suppress("UNCHECKED_CAST")
                    add(4u, "type",
                        definition = EnumDefinition(enum = NumberType),
                        getter = NumberDefinition<*>::type as (NumberDefinition<*>) -> NumberDescriptor<Comparable<Any>>?,
                        capturer = { context: NumericContext, value: NumberType ->
                            context.numberType = value.descriptor() as NumberDescriptor<Comparable<Any>>
                        },
                        fromSerializable = { value: NumberType? ->
                            value?.let {
                                it.descriptor() as NumberDescriptor<Comparable<Any>>
                            }
                        },
                        toSerializable = { value: NumberDescriptor<Comparable<Any>>?, _: NumericContext? ->
                            value?.type
                        }
                    )
                    add(5u, "minValue",
                        ContextualNumberDefinition<NumericContext>(required = false) {
                            it?.numberType ?: throw ContextNotFoundException()
                        },
                        getter = {
                            @Suppress("UNCHECKED_CAST")
                            it.minValue as Comparable<Any>?
                        }
                    )
                    add(6u, "maxValue",
                        ContextualNumberDefinition<NumericContext>(required = false) {
                            it?.numberType ?: throw ContextNotFoundException()
                        },
                        getter = {
                            @Suppress("UNCHECKED_CAST")
                            it.maxValue as Comparable<Any>?
                        }
                    )
                    add(7u, "default",
                        ContextualNumberDefinition<NumericContext>(required = false) {
                            it?.numberType ?: throw ContextNotFoundException()
                        },
                        getter = {
                            @Suppress("UNCHECKED_CAST")
                            it.default as Comparable<Any>?
                        }
                    )
                    IsNumericDefinition.addRandom(8u, this, NumberDefinition<*>::random)
                    add(9u, "reversedStorage", BooleanDefinition(required = false), NumberDefinition<*>::reversedStorage)
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<NumberDefinition<*>>) = NumberDefinition<Comparable<Any>>(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            type = values(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u),
            random = values(8u),
            reversedStorage = values(9u)
        )
    }
}

class NumericContext : IsPropertyContext {
    var numberType: NumberDescriptor<Comparable<Any>>? = null
}

fun <T : Comparable<T>> fromNativeType(type: NumberDescriptor<T>, value: Any) =
    when {
        type.isOfType(value) -> {
            @Suppress("UNCHECKED_CAST")
            value as T
        }
        value is Double -> type.ofDouble(value).also {
            if (type.toDouble(it) != value) {
                throw ParseException("$value not of expected type")
            }
        }
        value is Int -> type.ofInt(value)
        value is Long -> type.ofLong(value)
        else -> null
    }
