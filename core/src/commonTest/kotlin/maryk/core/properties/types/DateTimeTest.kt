package maryk.core.properties.types

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class DateTimeTest {
    private fun cleanToSeconds(it: LocalDateTime) = it.roundToDateUnit(DateUnit.Seconds)

    private val dateTime = LocalDateTime(
         2017,
        8,
        16,
        11,
        28,
        22,
        2344000
    )

    private val dateTimesWithSecondsToTest = arrayOf(
        cleanToSeconds(DateTimeDefinition.nowUTC()),
        cleanToSeconds(DateTimeDefinition.MAX_IN_SECONDS),
        cleanToSeconds(dateTime),
        DateTimeDefinition.MIN
    )

    private val dateTimesWithMillisToTest = arrayOf(
        DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Millis),
        DateTimeDefinition.MAX_IN_MILLIS,
        DateTimeDefinition.MIN
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        for (dateTime in dateTimesWithSecondsToTest) {
            bc.reserve(7)
            dateTime.writeBytes(TimePrecision.SECONDS, bc::write)
            expect(dateTime) { LocalDateTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        for (dateTime in dateTimesWithMillisToTest) {
            bc.reserve(9)
            dateTime.writeBytes(TimePrecision.MILLIS, bc::write)
            expect(dateTime) { LocalDateTime.fromByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        assertFailsWith<IllegalArgumentException> {
            LocalDateTime.fromByteReader(22) {
                1
            }
        }
    }
}
