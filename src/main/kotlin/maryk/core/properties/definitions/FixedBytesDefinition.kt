package maryk.core.properties.definitions

import maryk.core.extensions.randomBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Bytes

/** Definition for a bytes array with fixed length */
class FixedBytesDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Bytes? = null,
        maxValue: Bytes? = null,
        random: Boolean = false,
        override val byteSize: Int
): AbstractNumericDefinition<Bytes>(
    name, index, indexed, searchable, required, final, unique, minValue, maxValue, random
), IsFixedBytesEncodable<Bytes> {
    override fun createRandom() = Bytes(randomBytes(this.byteSize))

    override fun convertToBytes(value: Bytes, bytes: ByteArray?, offset: Int) = when(bytes) {
        null -> value.bytes
        else -> value.toBytes(bytes, offset)
    }

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = when{
        bytes.size != length && offset != 0 -> Bytes(bytes.copyOfRange(offset, offset + length))
        else -> Bytes(bytes)
    }

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = try {
        Bytes.ofBase64String(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }
}