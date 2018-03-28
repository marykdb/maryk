package maryk.core.properties.definitions

import maryk.core.properties.exceptions.OutOfRangeException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class IsComparableDefinitionTest {
    val test: String = "test"

    val def = StringDefinition(
        unique = true,
        minValue = "bbb",
        maxValue = "ddd"
    )

    @Test
    fun has_defined_values() {
        def.unique shouldBe true
        def.minValue shouldBe "bbb"
        def.maxValue shouldBe "ddd"
    }

    @Test
    fun validate_with_value_range() {
        def.validateWithRef(newValue = "ccc")
        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = "b")
        }

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = "z")
        }
    }
}