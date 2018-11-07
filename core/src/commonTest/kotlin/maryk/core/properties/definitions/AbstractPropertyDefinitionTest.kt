package maryk.core.properties.definitions

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class AbstractPropertyDefinitionTest {
    private val def = StringDefinition(final = true)

    @Test
    fun hasValues() {
        def.required shouldBe true
        def.final shouldBe true
    }

    @Test
    fun validateFinalProperty() {
        def.validateWithRef(newValue = "test")

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                previousValue = "old",
                newValue = "new"
            )
        }
    }

    @Test
    fun validateRequiredProperty() {
        def.validateWithRef(newValue = "test")

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }
}
