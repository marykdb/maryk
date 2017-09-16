package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
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
    fun convertStreamingBytes() {
        val byteCollector = ByteCollector()
        datesToTest.forEach {
            def.convertToBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertString() {
        datesToTest.forEach {
            val b = def.convertToString(it, optimized = false)
            def.convertFromString(b, optimized = false) shouldBe it
        }
    }

    @Test
    fun convertOptimizedString() {
        datesToTest.forEach {
            val b = def.convertToString(it, optimized = true)
            def.convertFromString(b, optimized = true) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong", optimized = false)
        }

        shouldThrow<ParseException> {
            def.convertFromString("wrong", optimized = true)
        }
    }
}