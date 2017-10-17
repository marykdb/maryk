package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Date
import org.junit.Test
import java.time.LocalDate

internal class DateDefinitionTest {
    private val datesToTest = arrayOf(
            Date.nowUTC(),
            Date.MAX,
            Date.MIN
    )

    val def = DateDefinition(
            name = "dateTime"
    )

    @Test
    fun createNow() {
        def.createNow().epochDay shouldBe LocalDate.now().toEpochDay()
    }

    @Test
    fun convertStorageBytes() {
        val byteCollector = GrowableByteCollector()
        datesToTest.forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertTransportBytes() {
        val byteCollector = GrowableByteCollector()
        datesToTest.forEach {
            def.writeTransportBytes(it, byteCollector::reserve, byteCollector::write)
            def.readTransportBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertString() {
        datesToTest.forEach {
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