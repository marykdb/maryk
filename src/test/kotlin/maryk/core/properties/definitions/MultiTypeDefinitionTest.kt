package maryk.core.properties.definitions

import maryk.Option
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

    val def = MultiTypeDefinition<Option, IsPropertyContext>(
            definitionMap = mapOf(
                    Option.V0 to stringDef,
                    Option.V1 to intDef
            )
    )

    val defMaxDefined = MultiTypeDefinition<Option, IsPropertyContext>(
            indexed = true,
            searchable = false,
            final = true,
            required = false,
            definitionMap = mapOf(
                    Option.V0 to stringDef,
                    Option.V1 to intDef
            )
    )

    private val multisToTest = arrayOf(
            TypedValue(Option.V0, "#test"),
            TypedValue(Option.V1, 400)
    )

    @Test
    fun `get properties`() {
        def.definitionMap[Option.V0] shouldBe stringDef
        def.definitionMap[Option.V1] shouldBe intDef
    }

    @Test
    fun `validate content`() {
        def.validateWithRef(newValue = TypedValue(Option.V0, "#test"))
        def.validateWithRef(newValue = TypedValue(Option.V1, 400))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(Option.V1, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(Option.V0, "WRONG"))
        }
    }

    @Test
    fun `invalid field should throw exception`() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(Option.V2, "NonExistingField"))
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