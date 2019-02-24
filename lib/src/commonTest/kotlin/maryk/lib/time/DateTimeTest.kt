package maryk.lib.time

import maryk.test.shouldBe
import kotlin.test.Test

internal class DateTimeTest {
    private fun cleanToSeconds(it: DateTime) = DateTime(it.date, Time(it.hour, it.minute, it.day))

    private val dateTime = DateTime(
        year = 2017,
        month = 8,
        day = 16,
        hour = 11,
        minute = 28,
        second = 22,
        milli = 2344
    )

    private val dateTimesWithSecondsToTest = arrayOf(
        cleanToSeconds(DateTime.nowUTC()),
        cleanToSeconds(DateTime.MAX_IN_SECONDS),
        cleanToSeconds(dateTime),
        DateTime.MIN
    )

    private val dateTimesWithMillisToTest = arrayOf(
        DateTime.nowUTC(),
        DateTime.MAX_IN_MILLIS,
        DateTime.MIN
    )

    @Test
    fun compare() {
        DateTime.MIN.compareTo(DateTime.MAX_IN_SECONDS) shouldBe -1999998
        DateTime.MAX_IN_MILLIS.compareTo(DateTime.MIN) shouldBe 1999998
        dateTime.compareTo(dateTime) shouldBe 0
    }

    @Test
    fun testGet() {
        this.dateTime.date shouldBe Date(2017, 8, 16)
        this.dateTime.time shouldBe Time(11, 28, 22, 2344)
    }

    @Test
    fun epochSecondConversion() {
        for (it in dateTimesWithSecondsToTest) {
            DateTime.ofEpochSecond(
                it.toEpochSecond()
            ) shouldBe it
        }
    }

    @Test
    fun epochMilliConversion() {
        for (it in arrayOf(
            DateTime.nowUTC()
        )) {
            DateTime.ofEpochMilli(
                it.toEpochMilli()
            ) shouldBe it
        }
    }

    @Test
    fun testStringConversion() {
        for (it in dateTimesWithMillisToTest) {
            DateTime.parse(
                it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun iso8601() {
        DateTime.parse("2014-02-28T09:34:43.123") shouldBe DateTime(2014, 2, 28, 9, 34, 43, 123)
        DateTime.parse("2014-02-28T09:34:43.123Z") shouldBe DateTime(2014, 2, 28, 9, 34, 43, 123)
        DateTime.parse("2015-05-24T12:03:55-05:00") shouldBe DateTime(2015, 5, 24, 17, 3, 55)
        DateTime.parse("2015-05-24T09:33:44+05:00") shouldBe DateTime(2015, 5, 24, 4, 33, 44)
    }
}
