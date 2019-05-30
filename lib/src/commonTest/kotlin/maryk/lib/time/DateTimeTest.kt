package maryk.lib.time

import kotlin.test.Test
import kotlin.test.expect

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
        expect(-1999998) { DateTime.MIN.compareTo(DateTime.MAX_IN_SECONDS) }
        expect(1999998) { DateTime.MAX_IN_MILLIS.compareTo(DateTime.MIN) }
        expect(0) { dateTime.compareTo(dateTime) }
    }

    @Test
    fun testGet() {
        expect(Date(2017, 8, 16)) { this.dateTime.date }
        expect(Time(11, 28, 22, 2344)) { this.dateTime.time }
    }

    @Test
    fun epochSecondConversion() {
        for (dateTime in dateTimesWithSecondsToTest) {
            expect(dateTime) {
                DateTime.ofEpochSecond(
                    dateTime.toEpochSecond()
                )
            }
        }
    }

    @Test
    fun epochMilliConversion() {
        for (dateTime in arrayOf(
            DateTime.nowUTC()
        )) {
            expect(dateTime) {
                DateTime.ofEpochMilli(dateTime.toEpochMilli())
            }
        }
    }

    @Test
    fun testStringConversion() {
        for (dateTime in dateTimesWithMillisToTest) {
            expect(dateTime) {
                DateTime.parse(dateTime.toString())
            }
        }
    }

    @Test
    fun iso8601() {
        expect(DateTime(2014, 2, 28, 9, 34, 43, 123)) {
            DateTime.parse("2014-02-28T09:34:43.123")
        }
        expect(DateTime(2014, 2, 28, 9, 34, 43, 123)) {
            DateTime.parse("2014-02-28T09:34:43.123Z")
        }
        expect(DateTime(2015, 5, 24, 17, 3, 55)) {
            DateTime.parse("2015-05-24T12:03:55-05:00")
        }
        expect(DateTime(2015, 5, 24, 4, 33, 44)) {
            DateTime.parse("2015-05-24T09:33:44+05:00")
        }
    }
}
