package maryk.core.properties.types

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.zeroFill
import maryk.core.properties.exceptions.ParseException
import maryk.core.time.Instant

/** Date by year, month and day. */
data class Date(
        override val year: Int,
        override val month: Byte,
        override val day: Byte
):
        DateInterface,
        IsTemporal<Date>()
{
    val epochDay: Long get() {
        val y = year.toLong()
        val m = month.toLong()
        var total = 0L
        total += 365 * y
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400
        } else {
            total -= y / -4 - y / -100 + y / -400
        }
        total += (367 * m - 362) / 12
        total += (day - 1).toLong()
        if (m > 2) {
            total--
            if (!isLeapYear(year)) {
                total--
            }
        }
        return total - DAYS_0000_TO_1970
    }

    override fun compareTo(other: Date): Int {
        var cmp = this.year - other.year
        if (cmp == 0) {
            cmp = this.month - other.month
            if (cmp == 0) {
                cmp = this.day - other.day
            }
        }
        return cmp
    }

    fun writeBytes(writer: (byte: Byte) -> Unit) {
        this.epochDay.writeBytes(writer)
    }

    /** Get value as ISO8601 string
     * (Overwrites data class toString)
     */
    override fun toString() = "$year-${month.zeroFill(2)}-${day.zeroFill(2)}"

    companion object: IsTemporalObject<Date>() {
        var MIN = Date(-99_999_999, 1, 1)
        var MAX = Date(99_999_999, 12, 31)

        /** The amount of days in 400 year cycle. */
        private const val DAYS_PER_CYCLE = 146097

        /** The amount of days from year zero to year 1970.
         * There are five 400 year cycles from year zero to 2000.
         * There are 7 leap years from 1970 to 2000. */
        private const val DAYS_0000_TO_1970 = DAYS_PER_CYCLE * 5L - (30L * 365L + 7L)

        private val dateRegex = Regex("^([-]?[1-9]\\d{0,8})-((?:0[1-9]|1[0-2]))-([0-3]\\d)$")

        /**
         * Checks if the year is a leap year, according to the ISO proleptic
         * calendar system rules.
         * @param year  the year to check
         * @return true if the year is leap, false otherwise
         */
        fun isLeapYear(year: Int) = year and 3 == 0 && (year % 100 != 0 || year % 400 == 0)

        /** Get a date by the amount of days since 01-01-1970 */
        fun ofEpochDay(epochDay: Long): Date {
            var zeroDay = epochDay + DAYS_0000_TO_1970
            // find the march-based year
            zeroDay -= 60  // adjust to 0000-03-01 so leap day is at end of four year cycle
            var adjust: Long = 0
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                val adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1
                adjust = adjustCycles * 400
                zeroDay += -adjustCycles * DAYS_PER_CYCLE
            }
            var yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE
            var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            if (doyEst < 0) {
                // fix estimate
                yearEst--
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            }
            yearEst += adjust  // reset any negative year
            val marchDoy0 = doyEst.toInt()

            // convert march-based values back to january-based
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
            yearEst += (marchMonth0 / 10).toLong()

            return Date(yearEst.toInt(), month.toByte(), dom.toByte())
        }

        /** Creates a date by reading a byte reader
         * @param reader to read from
         */
        fun fromByteReader(reader: () -> Byte) = Date.ofEpochDay(
            initLong(reader)
        )

        override fun parse(value: String): Date {
            val result = this.dateRegex.matchEntire(value) ?: throw ParseException(value)

            val (year, month, day) = result.destructured

            val y = year.toInt()
            val m = month.toByte()
            val d = day.toByte()

            when (m) {
                2.toByte() -> when {
                    d in (1..28) -> {}
                    d == 29.toByte() && isLeapYear(y) -> {}
                    else -> throw ParseException(value)
                }
                in byteArrayOf(1, 3, 5, 7, 8, 10, 12) -> when (d) {
                    in (1..31) -> {}
                    else -> throw ParseException(value)
                }
                in byteArrayOf(4, 6, 9, 11) -> when(d) {
                    in (1..30) -> {}
                    else -> throw ParseException(value)
                }
                else -> throw ParseException(value)
            }

            return Date(
                year = y,
                month = m,
                day = d
            )
        }

        /** Get the current date at UTC timezone */
        override fun nowUTC() = Date.ofEpochDay(
            (Instant.getCurrentEpochTimeInMillis() / MILLIS_PER_DAY)
        )
    }
}

interface DateInterface {
    val year: Int
    val month: Byte
    val day: Byte
}