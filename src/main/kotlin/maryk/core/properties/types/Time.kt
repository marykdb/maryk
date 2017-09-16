package maryk.core.properties.types

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.zeroFill
import maryk.core.properties.exceptions.ParseException
import maryk.core.time.Instant

enum class TimePrecision {
    SECONDS, MILLIS
}

/** Time with hours, minutes, seconds and millis */
data class Time(
    override val hour: Byte,
    override val minute: Byte,
    override val second: Byte = 0,
    override val milli: Short = 0
): IsTime<Time>(),
        TimeInterface,
        Comparable<Time>
{

    /** Get the seconds since midnight */
    val secondsOfDay: Int get() {
        var total = hour * SECONDS_PER_HOUR
        total += minute * SECONDS_PER_MINUTE
        total += second
        return total
    }

    /** Get the millis since midnight */
    private val millisOfDay: Int get() {
        var total = hour * MILLIS_PER_HOUR
        total += minute * MILLIS_PER_MINUTE
        total += second * MILLIS_PER_SECOND
        total += milli
        return total
    }

    override fun writeBytes(precision: TimePrecision, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        when (precision) {
            TimePrecision.MILLIS -> {
                reserver(4)
                (this.secondsOfDay * 1000 + this.milli).writeBytes(writer)
            }
            TimePrecision.SECONDS -> {
                reserver(3)
                this.secondsOfDay.writeBytes(writer, 3)
            }
        }
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
    override fun toString() = this.toString(true)

    override fun toString(iso8601: Boolean) = when {
        iso8601 -> {
            val out = "${hour.zeroFill(2)}:${minute.zeroFill(2)}"
            when {
                milli > 0 -> out + ":${second.zeroFill(2)}.${milli.zeroFill(3)}"
                second > 0 -> out + ":${second.zeroFill(2)}"
                else -> out
            }
        }
        else -> this.millisOfDay.toString()
    }

    companion object: IsTimeObject<Time>() {
        val MIN = Time(0, 0, 0)
        val MAX_IN_SECONDS = Time(23, 59, 59)
        val MAX_IN_MILLIS = Time(23, 59, 59, 999)
        val NOON = Time(12, 0, 0, 0)
        val MIDNIGHT = Time(0, 0, 0, 0)

        private val timeRegex = Regex("^((?:[01]\\d|2[0-3])):([0-5]\\d)(:([0-5]\\d)(.(\\d{3,9}))?)?$")

        /** Get Time with seconds since midnight */
        fun ofSecondOfDay(secondOfDay: Int): Time {
            if(secondOfDay !in (0 until SECONDS_PER_DAY)){
                throw ParseException("seconds of day out of reach for Time")
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
            if(milliOfDay !in (0 until MILLIS_PER_DAY)){
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

        override fun byteSize(precision: TimePrecision) = when (precision) {
            TimePrecision.MILLIS -> 4
            TimePrecision.SECONDS -> 3
        }

        override fun fromByteReader(length: Int, reader: () -> Byte): Time = when (length) {
            4 -> Time.ofMilliOfDay(initInt(reader))
            3 -> Time.ofSecondOfDay(initInt(reader, length))
            else -> throw IllegalArgumentException("Invalid length for bytes for Time conversion: $length")
        }

        @Throws(ParseException::class)
        override fun parse(value: String, iso8601: Boolean) = when {
            iso8601 -> {
                val result = timeRegex.matchEntire(value)
                        ?: throw ParseException("Invalid Time string: $value")
                val ( hour, minute, _, second, _,  milli) = result.destructured

                when {
                    milli.isNotBlank() -> {
                        Time(hour.toByte(), minute.toByte(), second.toByte(), milli.substring(0, 3).toShort())
                    }
                    second.isNotBlank() -> Time(hour.toByte(), minute.toByte(), second.toByte())
                    hour.isNotBlank() -> Time(hour.toByte(), minute.toByte())
                    else -> throw ParseException("Invalid Time string: $value")
                }
            }
            else -> {
                try {
                    Time.ofMilliOfDay(value.toInt())
                } catch (e: NumberFormatException) {
                    throw ParseException("Invalid Time string: $value")
                }
            }
        }

        override fun nowUTC(): Time {
            val nowInMillis = Instant.getCurrentEpochTimeInMillis()
            val millisOfDay = Math.floorMod(nowInMillis, MILLIS_PER_DAY.toLong()).toInt()
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
