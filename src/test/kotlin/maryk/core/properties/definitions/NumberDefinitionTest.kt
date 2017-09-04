package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test

internal class NumberDefinitionTest {
    private val def = NumberDefinition(
            name = "test",
            type = UInt32
    )

    private val intArray = arrayOf(UInt32.MIN_VALUE, UInt32.MAX_VALUE, 42373957.toUInt32())

    @Test
    fun hasValues() {
        def.type shouldBe UInt32
    }

    @Test
    fun createRandom() {
        def.createRandom()
    }

    @Test
    fun convertToBytes() {
        intArray.forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }

    @Test
    fun convertToString() {
        intArray.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }

    @Test
    fun convertToOptimizedString() {
        intArray.forEach {
            val b = def.convertToString(it, true)
            def.convertFromString(b, true) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong")
        }
    }
}