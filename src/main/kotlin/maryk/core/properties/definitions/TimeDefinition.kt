package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision

/**
 * Definition for Time properties
 */
class TimeDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Time? = null,
        maxValue: Time? = null,
        fillWithNow: Boolean = false,
        precision: TimePrecision = TimePrecision.SECONDS
) : AbstractTimeDefinition<Time>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue, fillWithNow, precision
), IsFixedBytesEncodable<Time> {
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = Time.ofBytes(bytes, offset, length)

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = Time.parse(string, iso8601 = !optimized)
}