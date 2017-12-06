package maryk.core.properties.definitions

import maryk.checkProtoBufConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class MultiTypeDefinitionTest {
    val intDef = NumberDefinition(
            type = SInt32,
            maxValue = 1000
    )

    val stringDef = StringDefinition(
            regEx = "#.*"
    )

    val def = MultiTypeDefinition(
            getDefinition = { when(it) {
                    0 -> stringDef
                    1 -> intDef
                    else -> null
            }}
    )

    val multisToTest = arrayOf(
            TypedValue(0, "#test"),
            TypedValue(1, 400)
    )

    @Test
    fun testGet() {
        def.getDefinition(0) shouldBe stringDef
        def.getDefinition(1) shouldBe intDef
    }

    @Test
    fun testValidation() {
        def.validateWithRef(newValue = TypedValue(0, "#test"))
        def.validateWithRef(newValue = TypedValue(1, 400))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(1, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(0, "WRONG"))
        }
    }

    @Test
    fun testValidationInvalidField() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(2, "NonExistingField"))
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }
}