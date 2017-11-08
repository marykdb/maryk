package maryk.core.properties.definitions

import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt64

/** Definition for Number properties */
class NumberDefinition<T: Comparable<T>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: T? = null,
        maxValue: T? = null,
        random: Boolean = false,
        val type: NumberDescriptor<T>
): AbstractNumericDefinition<T>(
    name, index, indexed, searchable, required, final, type.wireType, unique, minValue, maxValue, random
), IsFixedBytesEncodable<T> {
    override val byteSize = type.size

    override fun createRandom() = type.createRandom()

    override fun readStorageBytes(length: Int, reader: () -> Byte)
            = this.type.fromStorageByteReader(length, reader)

    override fun calculateStorageByteLength(value: T) = type.size

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)
            = this.type.writeStorageBytes(value, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?)
            = this.type.readTransportBytes(reader)

    override fun calculateTransportByteLength(value: T) = this.type.calculateTransportByteLength(value)

    override fun writeTransportBytes(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: IsPropertyContext?)
            = this.type.writeTransportBytes(value, writer)

    @Throws(ParseException::class)
    override fun fromString(string: String) = try {
        type.ofString(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }

    override fun writeJsonValue(value: T, writer: JsonWriter, context: IsPropertyContext?) = when {
        type !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
            writer.writeValue(
                    this.asString(value)
            )
        }
        else -> super.writeJsonValue(value, writer, context)
    }
}