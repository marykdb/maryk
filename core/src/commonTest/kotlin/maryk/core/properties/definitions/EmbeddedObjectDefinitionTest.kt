package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.ObjectDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class EmbeddedObjectDefinitionTest {
    private data class MarykObject(
        val string: String = "jur"
    ){
        object Properties : ObjectPropertyDefinitions<MarykObject>() {
            init {
                add(1, "string", StringDefinition(
                    regEx = "jur"
                ), MarykObject::string)
            }
        }
        companion object: ObjectDataModel<MarykObject, Properties>(
            name = "MarykObject",
            properties = Properties
        ) {
            override fun invoke(map: ObjectValues<MarykObject, Properties>) = MarykObject(
                map(1)
            )
        }
    }

    private val def = EmbeddedObjectDefinition(
        dataModel = { MarykObject }
    )
    private val defMaxDefined = EmbeddedObjectDefinition(
        indexed = true,
        required = false,
        final = true,
        dataModel = { MarykObject },
        default = MarykObject("default")
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe MarykObject
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = MarykObject())
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = MarykObject("wrong"))
        }
    }

    @Test
    fun convertObjectToJSONAndBack() {
        var output = ""
        val writer = JsonWriter(true) {
           output += it
        }
        val value = MarykObject()

        def.writeJsonValue(value, writer)

        var index = 0
        val reader = JsonReader {
            output[index++]
        }

        def.readJson(reader) shouldBe value
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = MarykObject()

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
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() })
        checkProtoBufConversion(this.defMaxDefined, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() })
        checkJsonConversion(this.defMaxDefined, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() })
        checkYamlConversion(this.defMaxDefined, EmbeddedObjectDefinition.Model, { DefinitionsConversionContext() }) shouldBe """
        indexed: true
        required: false
        final: true
        dataModel: MarykObject
        default:
          string: default

        """.trimIndent()
    }
}
