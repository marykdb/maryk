package maryk.core.properties.types

import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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
        for (it in timesWithSecondsToTest) {
            bc.reserve(3)
            it.writeBytes(TimePrecision.SECONDS, bc::write)
            Time.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (it in timesWithMillisToTest) {
            bc.reserve(4)
            it.writeBytes(TimePrecision.MILLIS, bc::write)
            Time.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        shouldThrow<IllegalArgumentException> {
            Time.fromByteReader(22) { 1 }
        }
    }
}
