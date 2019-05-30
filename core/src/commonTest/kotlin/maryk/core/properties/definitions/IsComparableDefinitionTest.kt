package maryk.core.properties.definitions

import maryk.core.properties.exceptions.OutOfRangeException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

internal class IsComparableDefinitionTest {
    val test: String = "test"

    val def = StringDefinition(
        unique = true,
        minValue = "bbb",
        maxValue = "ddd"
    )

    @Test
    fun hasDefinedValues() {
        assertTrue { def.unique }
        expect("bbb") { def.minValue }
        expect("ddd") { def.maxValue }
    }

    @Test
    fun validateWithValueRange() {
        def.validateWithRef(newValue = "ccc")
        assertFailsWith<OutOfRangeException> {
            def.validateWithRef(newValue = "b")
        }

        assertFailsWith<OutOfRangeException> {
            def.validateWithRef(newValue = "z")
        }
    }
}
