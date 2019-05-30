package maryk.core.properties.types

import maryk.lib.time.Time
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class TimeTest {
    private fun cleanToSeconds(time: Time) = Time(time.hour, time.minute, time.second)

    private val timesWithSecondsToTest = arrayOf(
        cleanToSeconds(Time.nowUTC()),
        Time.MAX_IN_SECONDS,
        Time.MIN
    )

    private val timesWithMillisToTest = arrayOf(
        Time.nowUTC(),
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
