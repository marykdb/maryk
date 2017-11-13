package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import kotlin.test.Test
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.test.shouldThrow

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