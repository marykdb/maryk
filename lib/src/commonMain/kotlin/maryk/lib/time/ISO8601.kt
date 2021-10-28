package maryk.lib.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** Object to convert native ISO8601 */
object ISO8601 {
    /** Decode [iso8601] string into DateTime */
    fun toDate(iso8601: String): DateTime = Instant.parse(iso8601).toLocalDateTime(TimeZone.UTC).run {
        DateTime(
            year = year,
            month = month.number.toByte(),
            day = dayOfMonth.toByte(),
            hour = hour.toByte(),
            minute = minute.toByte(),
            second = second.toByte(),
            milli = (nanosecond / 1000.0).roundToInt().toShort()
        )
    }
}
