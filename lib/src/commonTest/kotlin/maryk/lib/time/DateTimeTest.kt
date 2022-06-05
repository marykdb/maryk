package maryk.lib.time

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

internal class DateTimeTest {
    private val dateTime = LocalDateTime(
        year = 2017,
        monthNumber = 8,
        dayOfMonth = 16,
        hour = 11,
        minute = 28,
        second = 22,
        nanosecond = 2344000
    )

    @Test
    fun compare() {
        assertTrue { DateTime.MIN compareTo DateTime.MAX_IN_SECONDS < 0 }
        assertTrue { DateTime.MAX_IN_MILLIS compareTo DateTime.MIN > 0 }
        expect(0) { dateTime compareTo dateTime }
    }
}
