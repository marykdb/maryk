package maryk.core.properties.types

import maryk.lib.time.Time
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class DateTimeTest {
    private fun cleanToSeconds(it: maryk.lib.time.DateTime) = DateTime(it.date, Time(it.hour, it.minute, it.second))

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
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (dateTime in dateTimesWithSecondsToTest) {
            bc.reserve(7)
            dateTime.writeBytes(TimePrecision.SECONDS, bc::write)
            expect(dateTime) { DateTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (dateTime in dateTimesWithMillisToTest) {
            bc.reserve(9)
            dateTime.writeBytes(TimePrecision.MILLIS, bc::write)
            expect(dateTime) { DateTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        assertFailsWith<IllegalArgumentException> {
            DateTime.fromByteReader(22) {
                1
            }
        }
    }
}
