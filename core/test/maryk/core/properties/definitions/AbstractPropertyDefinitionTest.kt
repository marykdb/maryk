package maryk.core.properties.definitions

import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.RequiredException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class AbstractPropertyDefinitionTest {
    private val def = StringDefinition(final = true)

    @Test
    fun hasValues() {
        assertTrue { def.required }
        assertTrue { def.final }
    }

    @Test
    fun validateFinalProperty() {
        def.validateWithRef(newValue = "test")

        assertFailsWith<AlreadySetException> {
            def.validateWithRef(
                previousValue = "old",
                newValue = "new"
            )
        }
    }

    @Test
    fun validateRequiredProperty() {
        def.validateWithRef(newValue = "test")

        assertFailsWith<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }
}
