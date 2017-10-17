package maryk.core.properties.definitions

import maryk.core.json.JsonGenerator
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

    override fun convertFromStorageBytes(length: Int, reader:() -> Byte)
            = this.type.fromStorageByteReader(length, reader)

    override fun convertToStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)
            = this.type.writeStorageBytes(value, reserver, writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte)
            = this.type.readTransportBytes(reader)

    override fun writeTransportBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)
            = this.type.writeTransportBytes(value, reserver, writer)

    @Throws(ParseException::class)
    override fun convertFromString(string: String) = try {
        type.ofString(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }

    override fun writeJsonValue(generator: JsonGenerator, value: T) = when {
        type !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
            generator.writeValue(
                    this.convertToString(value)
            )
        }
        else -> super.writeJsonValue(generator, value)
    }
}