package maryk.core.properties.definitions

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class AbstractPropertyDefinitionTest {
    val def = StringDefinition(required = true, final = true)

    @Test
    fun hasValues() {
        def.required shouldBe true
        def.final shouldBe true
    }

    @Test
    fun validateFinal() {
        def.validateWithRef(newValue = "test")

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                    previousValue = "old",
                    newValue = "new"
            )
        }
    }

    @Test
    fun validateRequired() {
        def.validateWithRef(newValue = "test")

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }
}