package maryk.lib.time

import maryk.lib.exceptions.ParseException
import kotlin.math.floor

/** Date and Time object. */
data class DateTime(
    val date: Date,
    val time: Time
) :
    TimeInterface by time,
    DateInterface by date,
    IsTime<DateTime>()
{
    constructor(
        year: Int,
        month: Byte,
        day: Byte,
        hour: Byte,
        minute: Byte = 0,
        second: Byte = 0,
        milli: Short = 0
    ) : this(
        Date(year, month, day), Time(hour, minute, second, milli)
    )

    constructor(
        year: Int,
        month: Byte = 1,
        day: Byte = 1
    ) : this(
        Date(year, month, day), Time.MIDNIGHT
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
    override fun toString() = "${date}T$time"

    /** Get the date time as the amount of milliseconds since 01-01-1970 */
    fun toEpochMilli() = toEpochSecond() * MILLIS_PER_SECOND + time.milli

    /** Get the date time as the amount of seconds since 01-01-1970 */
    fun toEpochSecond() = date.epochDay.toLong() * SECONDS_PER_DAY + time.toSecondsOfDay()

    companion object : IsTimeObject<DateTime>() {
        var MIN = DateTime(Date.MIN, Time.MIN)
        var MAX_IN_SECONDS = DateTime(Date.MAX, Time.MAX_IN_SECONDS)
        var MAX_IN_MILLIS = DateTime(Date.MAX, Time.MAX_IN_MILLIS)

        /** Get a new DateTime with the date and time at UTC timezone */
        override fun nowUTC() = ofEpochMilli(
            Instant.getCurrentEpochTimeInMillis()
        )

        /** Create a DateTime by the amount of milliseconds since 01-01-1970 */
        fun ofEpochMilli(epochInMillis: Long): DateTime {
            val epochDay = floor((epochInMillis / MILLIS_PER_DAY).toDouble()).toInt()
            val millisOfDay = floor((epochInMillis % MILLIS_PER_DAY).toDouble()).toInt()
            return DateTime(
                Date.ofEpochDay(epochDay),
                Time.ofMilliOfDay(millisOfDay)
            )
        }

        /** Create a DateTime by the amount of seconds since 01-01-1970 */
        fun ofEpochSecond(epochInSeconds: Long, milli: Short = 0): DateTime {
            val epochDay = floor(epochInSeconds.toDouble() / SECONDS_PER_DAY).toInt()
            val secondOfDay = floor(epochInSeconds.toDouble() % SECONDS_PER_DAY).toInt()
            return DateTime(
                Date.ofEpochDay(epochDay),
                Time.ofMilliOfDay(
                    secondOfDay * 1000 + milli
                )
            )
        }

        override fun parse(value: String): DateTime {
            try {
                var (date, time) = value.split('T', limit = 2)
                if (time.contains("+") || time.contains("-")) {
                    return ISO8601.toDate(value)
                } else if (time.endsWith("Z")) {
                    time = time.removeSuffix("Z")
                }
                return DateTime(
                    Date.parse(date),
                    Time.parse(time)
                )
            } catch (e: Throwable) {
                throw ParseException(value, e)
            }
        }
    }
}
