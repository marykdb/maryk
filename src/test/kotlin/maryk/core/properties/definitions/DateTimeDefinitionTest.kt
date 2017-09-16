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

    val def = DateTimeDefinition(
            name = "seconds"
    )

    val defMilli = DateTimeDefinition(
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
    fun convertStreamingBytesMillis() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            defMilli.convertToBytes(it, byteCollector::reserve, byteCollector::write)
            defMilli.convertFromBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertStreamingBytesSeconds() {
        val byteCollector = ByteCollector()
        for(it in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            def.convertToBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertToBytesMilli() {
        for(value in arrayOf(DateTime.nowUTC(), DateTime.MAX_IN_MILLIS)) {
            val b = defMilli.convertToBytes(value)
            defMilli.convertFromBytes(b, 0, b.size) shouldBe value
        }
    }

    @Test
    fun convertToBytesSeconds() {
        for(value in arrayOf(DateTime.MAX_IN_SECONDS, DateTime.MIN)) {
            val b = def.convertToBytes(value)
            def.convertFromBytes(b, 0, b.size) shouldBe value
        }
    }

    @Test
    fun convertString() {
        dateTimesToTest.forEach {
            val b = def.convertToString(it, false)
            def.convertFromString(b, false) shouldBe it
        }
    }

    @Test
    fun convertOptimizedString() {
        dateTimesToTest.forEach {
            val b = def.convertToString(it, true)
            def.convertFromString(b, true) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong", false)
        }

        shouldThrow<ParseException> {
            def.convertFromString("wrong", true)
        }
    }
}
