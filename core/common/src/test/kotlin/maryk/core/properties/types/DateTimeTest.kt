package maryk.core.properties.types

import maryk.core.properties.ByteCollector
import maryk.lib.time.Time
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class DateTimeTest {
    private fun cleanToSeconds(it: maryk.lib.time.DateTime) = DateTime(it.date, Time(it.hour, it.minute, it.day))

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
        for (it in dateTimesWithSecondsToTest) {
            bc.reserve(7)
            it.writeBytes(TimePrecision.SECONDS, bc::write)
            DateTime.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (it in dateTimesWithMillisToTest) {
            bc.reserve(9)
            it.writeBytes(TimePrecision.MILLIS, bc::write)
            DateTime.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        shouldThrow<IllegalArgumentException> {
            DateTime.fromByteReader(22) {
                1
            }
        }
    }
}
