package maryk.lib.time

import kotlin.js.Date

/** Object to convert base 64 */
actual object ISO8601 {
    /** Decode [iso8601] string into DateTime */
    actual fun toDate(iso8601: String): DateTime {
        return Date(iso8601).let {
            DateTime(
                it.getUTCFullYear(),
                (it.getUTCMonth() + 1).toByte(),
                it.getUTCDate().toByte(),
                it.getUTCHours().toByte(),
                it.getUTCMinutes().toByte(),
                it.getUTCSeconds().toByte(),
                it.getMilliseconds().toShort()
            )
        }
    }
}
