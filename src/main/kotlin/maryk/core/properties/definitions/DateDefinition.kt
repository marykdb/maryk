package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Date

/**
 * Definition for Date properties
 */
class DateDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Date? = null,
        maxValue: Date? = null,
        fillWithNow: Boolean = false
) : AbstractMomentDefinition<Date>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue, fillWithNow
), IsFixedBytesEncodable<Date> {
    override val byteSize = 8

    override fun createNow() = Date.nowUTC()

    override fun convertFromBytes(length: Int, reader:() -> Byte) = Date.fromByteReader(reader)

    override fun convertToBytes(value: Date, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(reserver, writer)

    override fun convertToBytes(value: Date, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = Date.ofBytes(bytes, offset)

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = Date.parse(string, iso8601 = !optimized)
}