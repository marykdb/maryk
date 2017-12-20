package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class MultiTypeDefinitionTest {
    private val intDef = NumberDefinition(
            type = SInt32,
            maxValue = 1000
    )

    private val stringDef = StringDefinition(
            regEx = "#.*"
    )

    val def = MultiTypeDefinition<IsPropertyContext>(
            definitionMap = mapOf(
                    0 to stringDef,
                    1 to intDef
            )
    )

    val defMaxDefined = MultiTypeDefinition<IsPropertyContext>(
            indexed = true,
            searchable = false,
            final = true,
            required = false,
            definitionMap = mapOf(
                    0 to stringDef,
                    1 to intDef
            )
    )

    val multisToTest = arrayOf(
            TypedValue(0, "#test"),
            TypedValue(1, 400)
    )

    @Test
    fun `get properties`() {
        def.definitionMap[0] shouldBe stringDef
        def.definitionMap[1] shouldBe intDef
    }

    @Test
    fun `validate content`() {
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
    fun `invalid field should throw exception`() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(2, "NonExistingField"))
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollector()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, MultiTypeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, MultiTypeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }
}