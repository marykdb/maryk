package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class EmbeddedValuesDefinitionTest {
    object MarykModel: DataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val string = add(
                1, "string",
                StringDefinition(
                    regEx = "jur"
                )
            )
        }

        operator fun invoke(
            string: String = "jur"
        ) = this.map {
            mapNonNulls(
                this.string with string
            )
        }
    }

    private val def = EmbeddedValuesDefinition(
        dataModel = { MarykModel }
    )
    private val defMaxDefined = EmbeddedValuesDefinition(
        indexed = true,
        required = false,
        final = true,
        dataModel = { MarykModel },
        default = MarykModel("default")
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe MarykModel
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = MarykModel())
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = MarykModel("wrong"))
        }
    }

    @Test
    fun convert_object_to_JSON_and_back() {
        var output = ""
        val writer = JsonWriter(true) {
           output += it
        }
        val value = MarykModel()

        def.writeJsonValue(value, writer)

        var index = 0
        val reader = JsonReader {
            output[index++]
        }

        def.readJson(reader) shouldBe value
    }

    @Test
    fun convert_object_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = MarykModel()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(5, value, cache)
        )
        bc.bytes!!.size shouldBe 7
        def.writeTransportBytesWithKey(5, value, cache, bc::write, null)

        bc.bytes!!.toHex() shouldBe "2a050a036a7572"

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe 5

        def.readTransportBytes(
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
            bc::read
        ) shouldBe value
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        checkProtoBufConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        checkJsonConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        checkYamlConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() }) shouldBe """
        indexed: true
        required: false
        final: true
        dataModel: MarykModel
        default:
          string: default

        """.trimIndent()
    }
}
