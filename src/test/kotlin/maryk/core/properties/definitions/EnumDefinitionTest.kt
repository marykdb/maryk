package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.Option
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
            Option.V0,
            Option.V1
    )

    val def = EnumDefinition<Option>(
            name = "enum",
            values = Option.values()
    )

    @Test
    fun convertToBytes() {
        enumsToTest.forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }

    @Test
    fun convertStreamingBytes() {
        val byteCollector = ByteCollector()
        enumsToTest.forEach {
            def.convertToBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertToOffsetBytes() {
        enumsToTest.forEach {
            var b = ByteArray(20)
            b = def.convertToBytes(it, b, 10)
            def.convertFromBytes(b, 10, b.size) shouldBe it
        }
    }

    @Test
    fun convertString() {
        enumsToTest.forEach {
            val b = def.convertToString(it, optimized = false)
            def.convertFromString(b, optimized = false) shouldBe it
        }
    }

    @Test
    fun convertOptimizedString() {
        enumsToTest.forEach {
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