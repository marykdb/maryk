package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.exceptions.PropertyAlreadySetException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.test.shouldThrow
import kotlin.test.Test

internal class AbstractPropertyDefinitionTest {
    val def = StringDefinition(name = "test", required = true, final = true)

    @Test
    fun hasValues() {
        def.required shouldBe true
        def.final shouldBe true
    }

    @Test
    fun validateFinal() {
        def.validate(newValue = "test")

        shouldThrow<PropertyAlreadySetException> {
            def.validate(
                    previousValue = "old",
                    newValue = "new"
            )
        }
    }

    @Test
    fun validateRequired() {
        def.validate(newValue = "test")

        shouldThrow<PropertyRequiredException> {
            def.validate(newValue = null)
        }
    }
}