package maryk.core.properties.definitions

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import maryk.test.shouldBe
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

        shouldThrow<AlreadySetException> {
            def.validate(
                    previousValue = "old",
                    newValue = "new"
            )
        }
    }

    @Test
    fun validateRequired() {
        def.validate(newValue = "test")

        shouldThrow<RequiredException> {
            def.validate(newValue = null)
        }
    }
}