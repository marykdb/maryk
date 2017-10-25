package maryk.core.properties.types

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import org.junit.Test

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

    val dateTimesWithSecondsToTest = arrayOf(
            cleanToSeconds(DateTime.nowUTC()),
            cleanToSeconds(DateTime.MAX_IN_SECONDS),
            cleanToSeconds(dateTime),
            DateTime.MIN
    )

    val dateTimesWithMillisToTest = arrayOf(
            DateTime.nowUTC(),
            DateTime.MAX_IN_MILLIS,
            DateTime.MIN
    )
    
    @Test
    fun compare() {
        DateTime.MIN.compareTo(DateTime.MAX_IN_SECONDS) shouldBe -199999998
        DateTime.MAX_IN_MILLIS.compareTo(DateTime.MIN) shouldBe 199999998
        dateTime.compareTo(dateTime) shouldBe 0
    }

    @Test
    fun epochSecondConversion() {
        dateTimesWithSecondsToTest.forEach {
            DateTime.ofEpochSecond(
                    it.toEpochSecond()
            ) shouldBe it
        }
    }

    @Test
    fun epochMilliConversion() {
        arrayOf(
                DateTime.nowUTC()
        ).forEach {
            DateTime.ofEpochMilli(
                    it.toEpochMilli()
            ) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        dateTimesWithSecondsToTest.forEach {
            bc.reserve(7)
            it.writeBytes(TimePrecision.SECONDS, bc::write)
            DateTime.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        dateTimesWithMillisToTest.forEach {
            bc.reserve(9)
            it.writeBytes(TimePrecision.MILLIS, bc::write)
            DateTime.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        shouldThrow<IllegalArgumentException> {
            DateTime.fromByteReader(22){
                1
            }
        }
    }


    @Test
    fun testStringConversion() {
        dateTimesWithMillisToTest.forEach {
            DateTime.parse(
                    it.toString()
            ) shouldBe it
        }
    }
}