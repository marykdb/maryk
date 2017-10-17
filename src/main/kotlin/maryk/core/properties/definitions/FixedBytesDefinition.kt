package maryk.core.properties.definitions

import maryk.core.extensions.randomBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
class FixedBytesDefinition(
        name: String? = null,
        index: Int = -1,
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
    name, index, indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue, random
), IsFixedBytesEncodable<Bytes> {
    override fun createRandom() = Bytes(randomBytes(this.byteSize))

    override fun convertFromStorageBytes(length: Int, reader:() -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun convertToStorageBytes(value: Bytes, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(reserver, writer)

    @Throws(ParseException::class)
    override fun convertFromString(string: String) = try {
        Bytes.ofBase64String(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }
}