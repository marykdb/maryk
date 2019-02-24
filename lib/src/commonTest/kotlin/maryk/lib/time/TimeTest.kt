package maryk.lib.time

import maryk.lib.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class TimeTest {
    private val timesWithMillisToTest = arrayOf(
        Time.nowUTC(),
        Time.MAX_IN_MILLIS,
        Time.MIN
    )

    @Test
    fun compare() {
        Time.MIN.compareTo(Time.NOON) shouldBe -1
        Time.NOON.compareTo(Time.MIN) shouldBe 1
        Time.MIDNIGHT.compareTo(Time.MIDNIGHT) shouldBe 0
    }

    @Test
    fun testMillisOfDay() {
        Time(0, 0, 0, 0).toMillisOfDay() shouldBe 0
        Time(12, 3, 44, 345).toMillisOfDay() shouldBe 43424345
        Time(24, 59, 59, 999).toMillisOfDay() shouldBe 89999999
    }

    @Test
    fun testStringConversion() {
        for (it in timesWithMillisToTest) {
            Time.parse(
                it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun testWrongSizeError() {
        shouldThrow<ParseException> {
            Time.ofMilliOfDay(
                Int.MAX_VALUE
            )
        }

        shouldThrow<ParseException> {
            Time.ofSecondOfDay(
                Int.MAX_VALUE
            )
        }
    }
}
