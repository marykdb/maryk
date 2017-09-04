package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Int32
import org.junit.Test

internal class MultiTypeDefinitionTest {
    val intDef = NumberDefinition<Int>(
            name = "int",
            type = Int32,
            maxValue = 1000
    )

    val stringDef = StringDefinition(
            name = "string",
            regEx = "#.*"
    )

    val def = MultiTypeDefinition(
            name = "multitype",
            typeMap = mapOf(
                    0 to stringDef,
                    1 to intDef
            )
    )

    @Test
    fun testGet() {
        def.typeMap[0] shouldBe stringDef
        def.typeMap[1] shouldBe intDef
    }

    @Test
    fun testValidation() {
        def.validate(newValue = TypedValue(0, "#test"))
        def.validate(newValue = TypedValue(1, 400))

        shouldThrow<PropertyOutOfRangeException> {
            def.validate(newValue = TypedValue(1, 3000))
        }
        shouldThrow<PropertyInvalidValueException> {
            def.validate(newValue = TypedValue(0, "WRONG"))
        }
    }

    @Test
    fun testValidationInvalidField() {
        shouldThrow<DefNotFoundException> {
            def.validate(newValue = TypedValue(2, "NonExistingField"))
        }
    }
}