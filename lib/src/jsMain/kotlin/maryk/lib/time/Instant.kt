package maryk.lib.time

import kotlin.js.Date

actual object Instant {
    actual fun getCurrentEpochTimeInMillis() = Date().getTime().toLong()
}
