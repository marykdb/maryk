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
    private val timesToTestMillis = arrayOf(
            Time(12, 3, 5, 50),
            Time.nowUTC(),
            Time.MAX_IN_SECONDS,
            Time.MAX_IN_MILLIS,
            Time.MIN
    )

    private val timesToTestSeconds = arrayOf(Time.MAX_IN_SECONDS, Time.MIN, Time(13, 55, 44))

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
            LocalTime.now(ZoneOffset.UTC).toSecondOfDay() - def.createNow().toSecondsOfDay() in 0..1
        }
    }

    @Test
    fun convertStorageBytesMillis() {
        val byteCollector = ByteCollector()
        arrayOf(Time.MAX_IN_MILLIS, Time.MIN).forEach {
            defMilli.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            defMilli.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertStorageBytesSeconds() {
        val byteCollector = ByteCollector()
        timesToTestSeconds.forEach {
            def.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertTransportBytesSeconds() {
        val byteCollector = ByteCollector()
        timesToTestSeconds.forEach {
            def.writeTransportBytes(it, byteCollector::reserve, byteCollector::write)
            def.readTransportBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertTransportBytesMillis() {
        val byteCollector = ByteCollector()
        timesToTestMillis.forEach {
            defMilli.writeTransportBytes(it, byteCollector::reserve, byteCollector::write)
            defMilli.readTransportBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertString() {
        timesToTestMillis.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }
    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}
