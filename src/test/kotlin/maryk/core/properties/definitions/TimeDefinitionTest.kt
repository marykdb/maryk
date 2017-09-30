package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertTrue

internal class TimeDefinitionTest {
    private val timesToTest = arrayOf(
            Time(12, 3, 5, 50),
            Time.nowUTC(),
            Time.MAX_IN_SECONDS,
            Time.MAX_IN_MILLIS,
            Time.MIN
    )

    val def = TimeDefinition(
            name = "seconds"
    )

    val defMilli = TimeDefinition(
            name = "milli",
            precision = TimePrecision.MILLIS
    )

    @Test
    fun createNow() {
        assertTrue {
            LocalTime.now(ZoneOffset.UTC).toSecondOfDay() - def.createNow().secondsOfDay in 0..1
        }
    }

    @Test
    fun convertStreamingBytesMillis() {
        val byteCollector = ByteCollector()
        arrayOf(Time.MAX_IN_MILLIS, Time.MIN).forEach {
            defMilli.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            defMilli.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertStreamingBytesSeconds() {
        val byteCollector = ByteCollector()
        arrayOf(Time.MAX_IN_SECONDS, Time.MIN).forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertString() {
        timesToTest.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }
    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong")
        }
    }
}
