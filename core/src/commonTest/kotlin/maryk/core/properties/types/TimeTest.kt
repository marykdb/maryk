package maryk.core.properties.types

import kotlinx.datetime.LocalTime
import maryk.lib.time.Time
import maryk.lib.time.nowUTC
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class TimeTest {
    private fun cleanToSeconds(time: LocalTime) = LocalTime(time.hour, time.minute, time.second)
    private fun cleanToMilliSeconds(time: LocalTime) = LocalTime(time.hour, time.minute, time.second, time.nanosecond - (time.nanosecond % 1_000_000))

    private val timesWithSecondsToTest = arrayOf(
        cleanToSeconds(LocalTime.nowUTC()),
        Time.MAX_IN_SECONDS,
        Time.MIN
    )

    private val timesWithMillisToTest = arrayOf(
        cleanToMilliSeconds(LocalTime.nowUTC()),
        Time.MAX_IN_MILLIS,
        Time.MIN
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (time in timesWithSecondsToTest) {
            bc.reserve(3)
            time.writeBytes(TimePrecision.SECONDS, bc::write)
            expect(time) { Time.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (time in timesWithMillisToTest) {
            bc.reserve(4)
            time.writeBytes(TimePrecision.MILLIS, bc::write)
            expect(time) { Time.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        assertFailsWith<IllegalArgumentException> {
            Time.fromByteReader(22) { 1 }
        }
    }
}
