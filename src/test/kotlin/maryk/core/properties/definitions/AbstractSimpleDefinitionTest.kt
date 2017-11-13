package maryk.core.properties.definitions

import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class AbstractSimpleDefinitionTest {
    val test: String = "test"

    val def = StringDefinition(
            name = "test",
            unique = true,
            minValue = "bbb",
            maxValue = "ddd"
    )

    @Test
    fun hasValues() {
        def.unique shouldBe true
        def.minValue shouldBe "bbb"
        def.maxValue shouldBe "ddd"
    }

    @Test
    fun validateValueSize() {
        def.validate(newValue = "ccc")
        shouldThrow<PropertyOutOfRangeException> {
            def.validate(newValue = "b")
        }

        shouldThrow<PropertyOutOfRangeException> {
            def.validate(newValue = "z")
        }
    }
}