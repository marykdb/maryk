package maryk.core.properties.types

import kotlinx.datetime.LocalTime
import maryk.core.properties.definitions.TimeDefinition
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class TimeTest {
    private fun cleanToSeconds(time: LocalTime) = LocalTime(time.hour, time.minute, time.second)
    private fun cleanToMilliSeconds(time: LocalTime) = LocalTime(time.hour, time.minute, time.second, time.nanosecond - (time.nanosecond % 1_000_000))

    private val timesWithSecondsToTest = arrayOf(
        cleanToSeconds(TimeDefinition.nowUTC()),
        TimeDefinition.MAX_IN_SECONDS,
        TimeDefinition.MIN
    )

    private val timesWithMillisToTest = arrayOf(
        cleanToMilliSeconds(TimeDefinition.nowUTC()),
        TimeDefinition.MAX_IN_MILLIS,
        TimeDefinition.MIN
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (time in timesWithSecondsToTest) {
            bc.reserve(3)
            time.writeBytes(TimePrecision.SECONDS, bc::write)
            expect(time) { LocalTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (time in timesWithMillisToTest) {
            bc.reserve(4)
            time.writeBytes(TimePrecision.MILLIS, bc::write)
            expect(time) { LocalTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        assertFailsWith<IllegalArgumentException> {
            LocalTime.fromByteReader(22) { 1 }
        }
    }
}
