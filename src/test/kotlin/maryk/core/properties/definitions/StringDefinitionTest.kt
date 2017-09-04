package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.exceptions.PropertyInvalidValueException
import org.junit.Test

internal class StringDefinitionTest {

    private val stringsToTest = arrayOf("test123!@#éüî[]{}", "")

    val def = StringDefinition(
            minSize = 3,
            maxSize = 6,
            name = "test"
    )

    val defRegEx = StringDefinition(
            name = "test",
            regEx = "^[abcd]{3,4}$"
    )

    @Test
    fun validate() {
        // Should both succeed without errors
        def.validate(newValue = "abc")
        def.validate(newValue = "abcdef")

        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = "ab")
        }
        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = "abcdefg")
        }
    }

    @Test
    fun validateRegex() {
        // Should succeed
        defRegEx.validate(newValue = "abc")

        shouldThrow<PropertyInvalidValueException> {
            defRegEx.validate(newValue = "efgh")
        }
    }

    @Test
    fun convertToBytes() {
        stringsToTest.forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }

    @Test
    fun convertToBytesWithOffset() {
        stringsToTest.forEach {
            val bytes = ByteArray(33)
            val b = def.convertToBytes(it, bytes, 10)
            def.convertFromBytes(b, 10, it.toByteArray().size) shouldBe it
        }
    }

    @Test
    fun convertToString() {
        stringsToTest.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }
}