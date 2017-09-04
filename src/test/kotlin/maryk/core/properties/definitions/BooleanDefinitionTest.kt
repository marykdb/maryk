package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

internal class BooleanDefinitionTest {
    val def = BooleanDefinition(
            name = "test"
    )

    @Test
    fun convertToBytes() {
        booleanArrayOf(true, false).forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }

    @Test
    fun convertToString() {
        booleanArrayOf(true, false).forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }

    @Test
    fun convertToOptimizedString() {
        booleanArrayOf(true, false).forEach {
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