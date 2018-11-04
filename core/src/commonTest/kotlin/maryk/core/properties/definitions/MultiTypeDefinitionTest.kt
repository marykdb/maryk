package maryk.core.properties.definitions

import maryk.Option
import maryk.Option.V1
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.test.ByteCollector
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
        typeEnum = Option,
        definitionMap = mapOf(
            Option.V1 to stringDef,
            Option.V2 to intDef
        )
    )

    val defMaxDefined = MultiTypeDefinition<Option, IsPropertyContext>(
        indexed = true,
        final = true,
        required = false,
        typeEnum = Option,
        definitionMap = mapOf(
            Option.V1 to stringDef,
            Option.V2 to intDef
        ),
        default = TypedValue(Option.V1, "test")
    )

    private val multisToTest = arrayOf(
        TypedValue(Option.V1, "#test"),
        TypedValue(Option.V2, 400)
    )

    @Test
    fun get_properties() {
        def.definitionMap[Option.V1] shouldBe stringDef
        def.definitionMap[Option.V2] shouldBe intDef
    }

    @Test
    fun validate_content() {
        def.validateWithRef(newValue = TypedValue(Option.V1, "#test"))
        def.validateWithRef(newValue = TypedValue(Option.V2, 400))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(Option.V2, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(Option.V1, "WRONG"))
        }
    }

    @Test
    fun resolveReferenceByName() {
        def.resolveReferenceByName("*V1") shouldBe def.getTypeRef(V1, null)
    }

    @Test
    fun invalid_field_should_throw_exception() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(Option.V3, "NonExistingField"))
        }
    }

    @Test
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, MultiTypeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, MultiTypeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, MultiTypeDefinition.Model)
        checkYamlConversion(this.defMaxDefined, MultiTypeDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        typeEnum: Option
        definitionMap:
          ? 1: V1
          : !String
            indexed: false
            required: true
            final: false
            unique: false
            regEx: '#.*'
          ? 2: V2
          : !Number
            indexed: false
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 1000
            random: false
        default: !V1 test

        """.trimIndent()
    }
}