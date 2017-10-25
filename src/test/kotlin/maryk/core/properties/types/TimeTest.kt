package maryk.core.properties.types

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

internal class TimeTest {
    fun cleanToSeconds(time: Time) = Time(time.hour, time.minute, time.second)

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
    fun compare() {
        Time.MIN.compareTo(Time.NOON) shouldBe -1
        Time.NOON.compareTo(Time.MIN) shouldBe 1
        Time.MIDNIGHT.compareTo(Time.MIDNIGHT) shouldBe 0
    }

    @Test
    fun testMillisOfDay(){
        Time(0, 0, 0, 0).toMillisOfDay() shouldBe 0
        Time(12, 3, 44, 345).toMillisOfDay() shouldBe 43424345
        Time(24, 59, 59, 999).toMillisOfDay() shouldBe 89999999
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        timesWithSecondsToTest.forEach {
            bc.reserve(3)
            it.writeBytes(TimePrecision.SECONDS, bc::write)
            Time.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        timesWithMillisToTest.forEach {
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

    @Test
    fun testStringConversion() {
        timesWithMillisToTest.forEach {
            Time.parse(
                    it.toString()
            ) shouldBe it
        }
    }

    @Test
    fun testWrongSizeError() {
        shouldThrow<ParseException> {
            Time.ofMilliOfDay(
                    Int.MAX_VALUE
            )
        }

        shouldThrow<ParseException> {
            Time.ofSecondOfDay(
                    Int.MAX_VALUE
            )
        }
    }
}