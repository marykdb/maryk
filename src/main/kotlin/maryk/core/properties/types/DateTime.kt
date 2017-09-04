package maryk.core.properties.types

import maryk.core.extensions.bytes.initLongSeven
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.bytes.toSevenBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.time.Instant

/** Date and Time object. */
data class DateTime(
        val date: Date,
        val time: Time
):
        TimeInterface by time,
        DateInterface by date,
        IsTime<DateTime>()
{
    constructor(year: Int, month: Byte, day: Byte, hour: Byte = 0, minute: Byte = 0, second: Byte = 0, milli: Short = 0): this(
            Date(year, month, day), Time(hour, minute, second, milli)
    )

    override fun compareTo(other: DateTime): Int {
        var cmp = date.compareTo(other.date)
        if (cmp == 0) {
            cmp = time.compareTo(other.time)
        }
        return cmp
    }

    /**
     * Get value as ISO8601 string
     * (Overwrites data class toString)
     */
    override fun toString() = this.toString(true)

    override fun toString(iso8601: Boolean) = when {
        iso8601 -> "${date.toString(iso8601 = true)}T${time.toString(iso8601 = true)}"
        else -> "${this.toEpochSecond()},${this.milli}"
    }

    override fun toBytes(precision: TimePrecision, bytes: ByteArray?, offset: Int) = when (precision) {
        TimePrecision.MILLIS -> {
            val b = bytes ?: ByteArray(9)
            this.toEpochSecond().toSevenBytes(b, offset)
            this.milli.toBytes(b, offset + 7)
        }
        TimePrecision.SECONDS -> this.toEpochSecond().toSevenBytes(
                bytes ?: ByteArray(7),
                offset
        )
    }

    /** Get the date time as the amount of milliseconds since 01-01-1970 */
    fun toEpochMilli() = toEpochSecond() * MILLIS_PER_SECOND + time.milli

    /** Get the date time as the amount of seconds since 01-01-1970 */
    fun toEpochSecond() = date.epochDay * SECONDS_PER_DAY + time.secondsOfDay

    companion object: IsTimeObject<DateTime>() {
        var MIN = DateTime(Date.MIN, Time.MIN)
        var MAX_IN_SECONDS = DateTime(Date.MAX, Time.MAX_IN_SECONDS)
        var MAX_IN_MILLIS = DateTime(Date.MAX, Time.MAX_IN_MILLIS)

        /** Get a new DateTime with the date and time at UTC timezone */
        override fun nowUTC() = DateTime.ofEpochMilli(
                Instant.getCurrentEpochTimeInMillis()
        )

        /** Create a DateTime by the amount of milliseconds sinds 01-01-1970 */
        fun ofEpochMilli(epochInMillis: Long): DateTime {
            val epochDay = Math.floorDiv(epochInMillis, MILLIS_PER_DAY.toLong())
            val millisOfDay = Math.floorMod(epochInMillis, MILLIS_PER_DAY.toLong()).toInt()
            return DateTime(
                    Date.ofEpochDay(epochDay),
                    Time.ofMilliOfDay(millisOfDay)
            )
        }

        /** Create a DateTime by the amount of seconds sinds 01-01-1970 */
        fun ofEpochSecond(epochInSeconds: Long, milli: Short = 0): DateTime {
            val epochDay = Math.floorDiv(epochInSeconds, SECONDS_PER_DAY.toLong())
            val secondOfDay = Math.floorMod(epochInSeconds, SECONDS_PER_DAY.toLong()).toInt()
            return DateTime(
                    Date.ofEpochDay(epochDay),
                    Time.ofMilliOfDay(
                            secondOfDay * 1000 + milli
                    )
            )
        }

        override fun byteSize(precision: TimePrecision) = when (precision) {
            TimePrecision.MILLIS -> 9
            TimePrecision.SECONDS -> 7
        }

        /**
         * Converts byte array to DateTime
         * @param bytes  to convertFromBytes
         * @param offset of byte to start
         * @param length of bytes to convertFromBytes
         * @return DateTime represented by bytes
         */
        override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = when (length) {
            7 -> DateTime.ofEpochSecond(
                    initLongSeven(bytes, offset)
            )
            9 -> DateTime.ofEpochSecond(
                    initLongSeven(bytes, offset),
                    initShort(bytes, offset + 7)
            )
            else -> throw IllegalArgumentException("Invalid length for bytes for DateTime conversion: " + length)
        }

        override fun parse(value: String, iso8601: Boolean) = try {
            when {
                iso8601 -> {
                    val (date, time) = value.split('T', limit = 2)
                    DateTime(
                            Date.parse(date, iso8601 = true),
                            Time.parse(time)
                    )
                }
                else -> {
                    val i = value.indexOf(',')
                    DateTime.ofEpochSecond(
                            value.substring(0, i).toLong(),
                            value.substring(i + 1).toShort()
                    )
                }
            }
        } catch (e: Throwable) { throw ParseException(value, e) }
    }
}
