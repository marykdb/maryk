package maryk.core.properties.types

import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Object to convert base 64 */
actual object ISO8601 {
    /** Decode [iso8601] string into DateTime */
    actual fun toDate(iso8601: String): DateTime {
        return ZonedDateTime.parse(iso8601).withZoneSameInstant(ZoneOffset.UTC).let {
            DateTime(
                it.year,
                it.monthValue.toByte(),
                it.dayOfMonth.toByte(),
                it.hour.toByte(),
                it.minute.toByte(),
                it.second.toByte(),
                (it.nano / 1_000_000).toShort()
            )
        }
    }
}