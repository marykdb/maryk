package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
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

    @Suppress("unused")
    object Model :
        ContextualDataModel<NumberDefinition<*>, ObjectPropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>(
            contextTransformer = { NumericContext() },
            properties = object : ObjectPropertyDefinitions<NumberDefinition<*>>() {
                val required by boolean(1u, NumberDefinition<*>::required, default = true)
                val final by boolean(2u, NumberDefinition<*>::final, default = false)
                val unique by boolean(3u, NumberDefinition<*>::unique, default = false)

                @Suppress("UNCHECKED_CAST")
                val type by enum(
                    4u,
                    getter = NumberDefinition<*>::type as (NumberDefinition<*>) -> NumberDescriptor<Comparable<Any>>?,
                    enum = NumberType,
                    capturer = { context: NumericContext, value ->
                        context.numberType = value.descriptor() as NumberDescriptor<Comparable<Any>>
                    },
                    fromSerializable = { value ->
                        value?.let {
                            it.descriptor() as NumberDescriptor<Comparable<Any>>
                        }
                    },
                    toSerializable = { value, _ ->
                        value?.type
                    }
                )
                val minValue by contextual(
                    index = 5u,
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        it.minValue as Comparable<Any>?
                    },
                    definition = ContextualNumberDefinition<NumericContext>(required = false) {
                        it?.numberType ?: throw ContextNotFoundException()
                    }
                )
                val maxValue by contextual(
                    index = 6u,
                    definition = ContextualNumberDefinition<NumericContext>(required = false) {
                        it?.numberType ?: throw ContextNotFoundException()
                    },
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        it.maxValue as Comparable<Any>?
                    }
                )
                val default by contextual(
                    index = 7u,
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        it.default as Comparable<Any>?
                    },
                    definition = ContextualNumberDefinition<NumericContext>(required = false) {
                        it?.numberType ?: throw ContextNotFoundException()
                    }
                )
                val random by boolean(8u, NumberDefinition<*>::random, default = false)
                val reversedStorage by boolean(9u, NumberDefinition<*>::reversedStorage, required = false)
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


fun <T : Comparable<T>> PropertyDefinitions.number(
    index: UInt,
    type: NumberDescriptor<T>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: T? = null,
    maxValue: T? = null,
    default: T? = null,
    random: Boolean = false,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<T, T, IsPropertyContext, NumberDefinition<T>, Any>(
        index,
        name ?: propName,
        NumberDefinition(required, final, unique, minValue, maxValue, default, random, reversedStorage, type),
        alternativeNames
    )
}

fun <T : Comparable<T>, TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.number(
    index: UInt,
    getter: (DO) -> TO?,
    type: NumberDescriptor<T>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: T? = null,
    maxValue: T? = null,
    default: T? = null,
    random: Boolean = false,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<T, TO, IsPropertyContext, NumberDefinition<T>, DO>, DO> =
    number(index, getter, type, name, required, final,  unique, minValue, maxValue, default, random, reversedStorage, alternativeNames, toSerializable = null)

fun <T : Comparable<T>, TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.number(
    index: UInt,
    getter: (DO) -> TO?,
    type: NumberDescriptor<T>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: T? = null,
    maxValue: T? = null,
    default: T? = null,
    random: Boolean = false,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> T?)? = null,
    fromSerializable: (Unit.(T?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, T) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        NumberDefinition(required, final, unique, minValue, maxValue, default, random, reversedStorage, type),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
