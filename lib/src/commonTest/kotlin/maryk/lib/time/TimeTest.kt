package maryk.lib.time

import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class TimeTest {
    private val timesWithMillisToTest = arrayOf(
        Time.nowUTC(),
        Time.MAX_IN_MILLIS,
        Time.MIN
    )

    @Test
    fun compare() {
        expect(-1) { Time.MIN compareTo Time.NOON }
        expect(1) { Time.NOON compareTo Time.MIN }
        expect(0) { Time.MIDNIGHT compareTo Time.MIDNIGHT }
    }

    @Test
    fun testMillisOfDay() {
        expect(0) { Time(0, 0, 0, 0).toMillisOfDay() }
        expect(43424345) { Time(12, 3, 44, 345).toMillisOfDay() }
        expect(89999999) { Time(24, 59, 59, 999).toMillisOfDay() }
    }

    @Test
    fun testStringConversion() {
        for (time in timesWithMillisToTest) {
            expect(time) {
                Time.parse(time.toString())
            }
        }
    }

    @Test
    fun testWrongSizeError() {
        assertFailsWith<ParseException> {
            Time.ofMilliOfDay(
                Int.MAX_VALUE
            )
        }

        assertFailsWith<ParseException> {
            Time.ofSecondOfDay(
                Int.MAX_VALUE
            )
        }
    }
}
