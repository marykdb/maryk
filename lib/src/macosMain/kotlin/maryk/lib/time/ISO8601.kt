package maryk.lib.time

import maryk.lib.exceptions.ParseException
import platform.CoreFoundation.kCFISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT

/** Object to convert native ISO8601 */
actual object ISO8601 {
    private val utcTimeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    private val formatter = NSISO8601DateFormatter()

    init {
        formatter.formatOptions = kCFISO8601DateFormatWithInternetDateTime
        formatter.timeZone = utcTimeZone
    }

    /** Decode [iso8601] string into DateTime */
    actual fun toDate(iso8601: String): DateTime {
        return formatter.dateFromString(iso8601)?.let { date ->
            val components = NSCalendar.currentCalendar.componentsInTimeZone(utcTimeZone, date)
            DateTime(
                components.year.toInt(),
                components.month.toByte(),
                components.day.toByte(),
                components.hour.toByte(),
                components.minute.toByte(),
                components.second.toByte(),
                (components.nanosecond / 1_000_000).toShort()
            )
        } ?: throw ParseException("ISO8601 date invalid $iso8601")
    }
}
