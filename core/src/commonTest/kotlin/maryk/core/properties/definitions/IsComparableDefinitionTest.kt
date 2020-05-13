package maryk.core.properties.definitions

import maryk.core.properties.exceptions.OutOfRangeException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    @Test
    fun isCompatible() {
        assertTrue {
            StringDefinition(
                unique = true
            ).compatibleWith(def)
        }

        assertTrue {
            StringDefinition(
                unique = true,
                minValue = "aaa",
                maxValue = "eee"
            ).compatibleWith(def)
        }

        assertFalse {
            StringDefinition(
                unique = false
            ).compatibleWith(def)
        }

        assertFalse {
            StringDefinition(
                unique = true,
                minValue = "ccc"
            ).compatibleWith(def)
        }

        assertFalse {
            StringDefinition(
                unique = true,
                maxValue = "ccc"
            ).compatibleWith(def)
        }

        assertFalse {
            StringDefinition(
                unique = true
            ).compatibleWith(StringDefinition())
        }
    }
}
