package maryk.lib.time

import kotlinx.datetime.Clock
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.zeroFill
import kotlin.math.floor

/** Time with hours, minutes, seconds and millis */
data class Time(
    override val hour: Byte,
    override val minute: Byte,
    override val second: Byte = 0,
    override val milli: Short = 0
) : TimeInterface,
    Comparable<Time>
{

    /** Get the seconds since midnight */
    fun toSecondsOfDay(): Int {
        var total = hour * SECONDS_PER_HOUR
        total += minute * SECONDS_PER_MINUTE
        total += second
        return total
    }

    /** Get the millis since midnight */
    fun toMillisOfDay(): Int {
        var total = hour * MILLIS_PER_HOUR
        total += minute * MILLIS_PER_MINUTE
        total += second * MILLIS_PER_SECOND
        total += milli
        return total
    }

    override fun compareTo(other: Time): Int {
        var cmp = hour.compareTo(other.hour)
        if (cmp == 0) {
            cmp = minute.compareTo(other.minute)
            if (cmp == 0) {
                cmp = second.compareTo(other.second)
                if (cmp == 0) {
                    cmp = milli.compareTo(other.milli)
                }
            }
        }
        return cmp
    }

    /**
     * Get value as ISO8601 string
     * (Overwrites data class toString)
     */
    override fun toString(): String {
        val out = "${hour.zeroFill(2)}:${minute.zeroFill(2)}"
        return when {
            milli > 0 -> out + ":${second.zeroFill(2)}.${milli.zeroFill(3)}"
            second > 0 -> out + ":${second.zeroFill(2)}"
            else -> out
        }
    }

    companion object {
        val MIN = Time(0, 0, 0)
        val MAX_IN_SECONDS = Time(23, 59, 59)
        val MAX_IN_MILLIS = Time(23, 59, 59, 999)
        val NOON = Time(12, 0, 0, 0)
        val MIDNIGHT = Time(0, 0, 0, 0)

        private val timeRegex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)(:([0-5]\\d)(.(\\d{3,9}))?)?$")

        /** Get Time with seconds since midnight */
        fun ofSecondOfDay(secondOfDay: Int): Time {
            if (secondOfDay !in (0 until SECONDS_PER_DAY)) {
                throw ParseException("seconds of day out of reach for Time: $secondOfDay in $SECONDS_PER_DAY")
            }
            val hours = (secondOfDay / SECONDS_PER_HOUR)
            var substractedSeconds = secondOfDay - (hours * SECONDS_PER_HOUR).toLong()
            val minutes = (substractedSeconds / SECONDS_PER_MINUTE).toInt()
            substractedSeconds -= (minutes * SECONDS_PER_MINUTE).toLong()
            return Time(
                hour = hours.toByte(),
                minute = minutes.toByte(),
                second = substractedSeconds.toByte()
            )
        }

        /** Get Time with millis since midnight */
        fun ofMilliOfDay(milliOfDay: Int): Time {
            if (milliOfDay !in (0 until MILLIS_PER_DAY)) {
                throw ParseException("Milli of day $milliOfDay out of reach for Time")
            }
            val hours = milliOfDay / MILLIS_PER_HOUR
            var substractedMilliOfDay = milliOfDay - (hours * MILLIS_PER_HOUR)
            val minutes = substractedMilliOfDay / MILLIS_PER_MINUTE
            substractedMilliOfDay -= minutes * MILLIS_PER_MINUTE
            val seconds = substractedMilliOfDay / MILLIS_PER_SECOND
            substractedMilliOfDay -= seconds * MILLIS_PER_SECOND
            return Time(
                hour = hours.toByte(),
                minute = minutes.toByte(),
                second = seconds.toByte(),
                milli = substractedMilliOfDay.toShort()
            )
        }

        fun parse(value: String): Time {
            val result = timeRegex.matchEntire(value)
                ?: throw ParseException("Invalid Time string: $value")
            val (hour, minute, _, second, _, milli) = result.destructured

            return when {
                milli.isNotBlank() -> {
                    Time(hour.toByte(), minute.toByte(), second.toByte(), milli.substring(0, 3).toShort())
                }
                second.isNotBlank() -> Time(hour.toByte(), minute.toByte(), second.toByte())
                hour.isNotBlank() -> Time(hour.toByte(), minute.toByte())
                else -> throw ParseException("Invalid Time string: $value")
            }
        }

        fun nowUTC(): Time {
            val nowInMillis = Clock.System.now().toEpochMilliseconds()
            val millisOfDay = floor(nowInMillis.toDouble() % MILLIS_PER_DAY).toInt()
            return ofMilliOfDay(millisOfDay)
        }
    }
}

interface TimeInterface {
    val hour: Byte
    val minute: Byte
    val second: Byte
    val milli: Short
}
