package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.NumberDescriptor

/** Definition for Number properties */
class NumberDefinition<T: Comparable<T>>(
        name: String? = null,
        index: Short = -1,
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
    name, index, indexed, searchable, required, final, unique, minValue, maxValue, random
), IsFixedBytesEncodable<T> {
    override val byteSize = type.size

    override fun createRandom() = type.createRandom()

    override fun convertToBytes(value: T, bytes: ByteArray?, offset: Int) = type.toBytes(value,bytes?: ByteArray(byteSize), offset)

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = type.ofBytes(bytes, offset, length)

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = try {
        type.ofString(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }
}