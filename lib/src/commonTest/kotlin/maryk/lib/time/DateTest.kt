package maryk.lib.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

internal class DateTest {
    @Test
    fun compare() {
        assertTrue { Date.MIN compareTo Date.MAX < 0 }
        assertTrue { Date.MAX compareTo Date.MIN > 0 }
        expect(0) { LocalDate.nowUTC() compareTo LocalDate.nowUTC() }
    }
}
