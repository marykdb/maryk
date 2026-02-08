package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
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
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.yaml.YamlWriter
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
        UInt64 -> {
            val stringValue = this.asString(value)
            if (writer is YamlWriter && (value as? ULong)?.let { it <= Long.MAX_VALUE.toULong() } == true) {
                writer.writeValue(stringValue)
            } else {
                writer.writeString(stringValue)
            }
        }
        Float64, Float32 -> {
            val stringValue = this.asString(value)
            if (writer is YamlWriter) {
                writer.writeValue(stringValue)
            } else {
                writer.writeString(stringValue)
            }
        }
        SInt64 -> super.writeJsonValue(value, writer, context)
        else -> {
            writer.writeValue(
                this.asString(value)
            )
        }
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsNumericDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        (definition as? NumberDefinition<*>)?.let {
            if (definition.type != this.type) {
                addIncompatibilityReason?.invoke("Number definition has to be of the same type. Now $type instead of ${definition.type}")
                compatible = false
            }

            if (definition.reversedStorage != this.reversedStorage) {
                addIncompatibilityReason?.invoke("Reversed storage for number has to be the same on both definitions")
                compatible = false
            }
        }

        return compatible
    }

    object Model : ContextualDataModel<NumberDefinition<*>, Model, IsPropertyContext, NumericContext>(
        contextTransformer = { NumericContext() },
    ) {
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
        val reversedStorage by boolean(8u, NumberDefinition<*>::reversedStorage, required = false)

        override fun invoke(values: ObjectValues<NumberDefinition<*>, Model>) = NumberDefinition<Comparable<Any>>(
            required = values(required.index),
            final = values(final.index),
            unique = values(unique.index),
            type = values(type.index),
            minValue = values(minValue.index),
            maxValue = values(maxValue.index),
            default = values(default.index),
            reversedStorage = values(reversedStorage.index)
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
        value is Int -> type.ofInt(value).also {
            ensureIntegralConversionFits(type, value.toString(), it)
        }
        value is Long -> type.ofLong(value).also {
            ensureIntegralConversionFits(type, value.toString(), it)
        }
        else -> null
    }

private fun <T : Comparable<T>> ensureIntegralConversionFits(
    type: NumberDescriptor<T>,
    originalValue: String,
    convertedValue: T
) {
    if (type == Float32 || type == Float64) return
    if (convertedValue.toString() != originalValue) {
        throw ParseException("$originalValue not of expected type")
    }
}

fun <T : Comparable<T>> IsValuesDataModel.number(
    index: UInt,
    type: NumberDescriptor<T>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: T? = null,
    maxValue: T? = null,
    default: T? = null,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<T, T, IsPropertyContext, NumberDefinition<T>, Any>(
        index,
        name ?: propName,
        NumberDefinition(required, final, unique, minValue, maxValue, default, reversedStorage, type),
        alternativeNames
    )
}

fun <T : Comparable<T>, TO: Any, DO: Any> IsObjectDataModel<DO>.number(
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
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<T, TO, IsPropertyContext, NumberDefinition<T>, DO>, DO, IsPropertyContext> =
    number(index, getter, type, name, required, final,  unique, minValue, maxValue, default, reversedStorage, alternativeNames, toSerializable = null)

fun <T : Comparable<T>, TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.number(
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
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> T?)? = null,
    fromSerializable: ((T?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, T) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        NumberDefinition(required, final, unique, minValue, maxValue, default, reversedStorage, type),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
