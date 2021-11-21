package maryk.lib.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.expect

internal class DateTest {
    @Test
    fun compare() {
        expect(-1999998) { Date.MIN compareTo Date.MAX }
        expect(1999998) { Date.MAX compareTo Date.MIN }
        expect(0) { LocalDate.nowUTC() compareTo LocalDate.nowUTC() }
    }
}
