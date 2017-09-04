package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision

/**
 * Definition for DateTime properties
 */
class DateTimeDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = true,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: DateTime? = null,
        maxValue: DateTime? = null,
        fillWithNow: Boolean = false,
        precision: TimePrecision = TimePrecision.SECONDS
) : AbstractTimeDefinition<DateTime>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue, fillWithNow, precision
), IsFixedBytesEncodable<DateTime> {
    override val byteSize = DateTime.byteSize(precision)

    override fun createNow() = DateTime.nowUTC()

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = DateTime.ofBytes(bytes, offset, length)

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = DateTime.parse(string, iso8601 = !optimized)
}