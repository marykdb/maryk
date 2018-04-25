package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCacheReader
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** Definition for Number properties */
data class NumberDefinition<T: Comparable<T>>(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: T? = null,
    override val maxValue: T? = null,
    override val random: Boolean = false,
    val type: NumberDescriptor<T>
):
    IsNumericDefinition<T>,
    IsSerializableFixedBytesEncodable<T, IsPropertyContext>,
    IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.Number
    override val wireType = type.wireType
    override val byteSize = type.size

    override fun createRandom() = type.createRandom()

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        this.type.fromStorageByteReader(length, reader)

    override fun calculateStorageByteLength(value: T) = type.size

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) =
        this.type.writeStorageBytes(value, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) =
        this.type.readTransportBytes(reader)

    override fun calculateTransportByteLength(value: T) = this.type.calculateTransportByteLength(value)

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) =
        this.type.writeTransportBytes(value, writer)

    override fun fromString(string: String) = try {
        type.ofString(string)
    } catch (e: Throwable) { throw ParseException(string, e) }

    override fun fromNativeType(value: Any) = fromNativeType(this.type, value)

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: IsPropertyContext?) = when (type) {
        !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
            writer.writeValue(
                this.asString(value)
            )
        }
        else -> super.writeJsonValue(value, writer, context)
    }

    internal object Model : ContextualDataModel<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>(
        contextTransformer = { NumericContext },
        properties = object : PropertyDefinitions<NumberDefinition<*>>() {
            init {
                IsPropertyDefinition.addIndexed(this, NumberDefinition<*>::indexed)
                IsPropertyDefinition.addSearchable(this, NumberDefinition<*>::searchable)
                IsPropertyDefinition.addRequired(this, NumberDefinition<*>::required)
                IsPropertyDefinition.addFinal(this, NumberDefinition<*>::final)
                IsComparableDefinition.addUnique(this, NumberDefinition<*>::unique)
                add(5, "type", ContextCaptureDefinition(
                    definition = EnumDefinition(values = NumberType.values()),
                    capturer = { context: NumericContext?, value ->
                        context?.apply {
                            @Suppress("UNCHECKED_CAST")
                            numberType = value.descriptor() as NumberDescriptor<Comparable<Any>>
                        } ?: throw ContextNotFoundException()
                    }
                )) {
                    it.type.type
                }
                add(6, "minValue", ContextualNumberDefinition<NumericContext>(required = false) {
                    it?.numberType ?: throw ContextNotFoundException()
                }) {
                    @Suppress("UNCHECKED_CAST")
                    it.minValue as Comparable<Any>?
                }
                add(7, "maxValue", ContextualNumberDefinition<NumericContext>(required = false) {
                    it?.numberType ?: throw ContextNotFoundException()
                }) {
                    @Suppress("UNCHECKED_CAST")
                    it.maxValue as Comparable<Any>?
                }
                IsNumericDefinition.addRandom(8,this, NumberDefinition<*>::random)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = NumberDefinition(
            indexed = map[0] as Boolean? ?: false,
            searchable = map[1] as Boolean? ?: true,
            required = map[2] as Boolean? ?: true,
            final = map[3] as Boolean? ?: false,
            unique = map[4] as Boolean? ?: false,
            type = (map[5] as NumberType).descriptor() as NumberDescriptor<Comparable<Any>>,
            minValue = map[6] as Comparable<Any>?,
            maxValue = map[7] as Comparable<Any>?,
            random = map[8] as Boolean? ?: false
        )
    }
}

internal object NumericContext : IsPropertyContext {
    var numberType: NumberDescriptor<Comparable<Any>>? = null
}

fun <T: Comparable<T>> fromNativeType(type: NumberDescriptor<T>, value: Any) =
    if (type.isOfType(value)) {
        @Suppress("UNCHECKED_CAST")
        value as T
    } else if (value is Double) {
        type.ofDouble(value).also {
            if (it != value) {
                throw ParseException("$value not of expected type")
            }
        }
    } else if (value is Int) {
        type.ofInt(value)
    } else if (value is Long) {
        type.ofLong(value)
    } else {
        null
    }
