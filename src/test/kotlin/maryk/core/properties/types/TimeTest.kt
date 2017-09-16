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
    fun testBytesConversion() {
        timesWithSecondsToTest.forEach {
            Time.ofBytes(
                    it.toBytes(TimePrecision.SECONDS)
            ) shouldBe it
        }
    }

    @Test
    fun testBytesOffsetConversion() {
        timesWithSecondsToTest.forEach {
            val bytes = ByteArray(22)
            Time.ofBytes(
                    it.toBytes(TimePrecision.SECONDS, bytes, 10),
                    10,
                    3
            ) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        timesWithSecondsToTest.forEach {
            it.writeBytes(TimePrecision.SECONDS, bc::reserve, bc::write)
            Time.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testBytesMillisConversion() {
        timesWithMillisToTest.forEach {
            Time.ofBytes(
                    it.toBytes(TimePrecision.MILLIS)
            ) shouldBe it
        }
    }

    @Test
    fun testBytesMillisOffsetConversion() {
        timesWithMillisToTest.forEach {
            val bytes = ByteArray(22)
            Time.ofBytes(
                    it.toBytes(TimePrecision.MILLIS, bytes, 10),
                    10,
                    4
            ) shouldBe it
        }
    }

    @Test
    fun testStreamingMillisConversion() {
        val bc = ByteCollector()
        timesWithMillisToTest.forEach {
            it.writeBytes(TimePrecision.MILLIS, bc::reserve, bc::write)
            Time.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testWrongByteSizeError() {
        val bytes = ByteArray(22)
        shouldThrow<IllegalArgumentException> {
            Time.ofBytes(
                    bytes,
                    10,
                    22
            )
        }
    }

    @Test
    fun testStringConversion() {
        timesWithMillisToTest.forEach {
            Time.parse(
                    it.toString(true),
                    iso8601 = true
            ) shouldBe it
        }
    }

    @Test
    fun testOptimizedStringConversion() {
        timesWithMillisToTest.forEach {
            Time.parse(
                    it.toString(iso8601 = false),
                    iso8601 = false
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