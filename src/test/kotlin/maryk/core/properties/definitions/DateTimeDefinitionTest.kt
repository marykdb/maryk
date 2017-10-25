package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertTrue

internal class DateTimeDefinitionTest {
    private val dateTimesToTest = arrayOf(
            DateTime.nowUTC(),
            DateTime.MAX_IN_SECONDS,
            DateTime.MAX_IN_MILLIS,
            DateTime.MIN
    )

    private val def = DateTimeDefinition(
            name = "seconds"
    )

    private val defMilli = DateTimeDefinition(
            name = "milli",
            precision = TimePrecision.MILLIS
    )

    @Test
    fun createNow() {
        assertTrue {
            LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli() - def.createNow().toEpochMilli() in -20..20
        }
    }

    @Test
    fun convertStorageBytesMillis() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            defMilli.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            defMilli.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertStorageBytesSeconds() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            def.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertTransportBytesMillis() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.MIN, DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            byteCollector.reserve(defMilli.reserveTransportBytes(it))
            defMilli.writeTransportBytes(it, byteCollector::write)
            defMilli.readTransportBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertTransportBytesSeconds() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            byteCollector.reserve(def.reserveTransportBytes(it))
            def.writeTransportBytes(it, byteCollector::write)
            def.readTransportBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertString() {
        dateTimesToTest.forEach {
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
