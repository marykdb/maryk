package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.DataModel
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class EmbeddedValuesDefinitionTest {
    object MarykModel : DataModel<MarykModel>() {
        val string by string(
            index = 1u,
            regEx = "jur",
            default = "jur",
        )
    }

    private val def = EmbeddedValuesDefinition(
        dataModel = { MarykModel }
    )
    private val defMaxDefined = EmbeddedValuesDefinition(
        required = false,
        final = true,
        dataModel = { MarykModel },
        default = MarykModel.run { create(string with "default") }
    )

    @Test
    fun hasValues() {
        expect(MarykModel) { def.dataModel }
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = MarykModel.create())
        assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = MarykModel.run { create(string with "wrong") })
        }
    }

    @Test
    fun convertObjectToJSONAndBack() {
        val value = MarykModel.create()

        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            def.writeJsonValue(value, writer)
        }

        var index = 0
        val reader = JsonReader {
            output[index++]
        }

        expect(value) { def.readJson(reader) }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = MarykModel.create()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(5, value, cache)
        )
        expect(7) { bc.bytes!!.size }
        def.writeTransportBytesWithKey(5, value, cache, bc::write, null)

        expect("2a050a036a7572") { bc.bytes!!.toHex() }

        val key = ProtoBuf.readKey(bc::read)
        expect(LENGTH_DELIMITED) { key.wireType }
        expect(5u) { key.tag }

        expect(value) {
            def.readTransportBytes(
                ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
                bc::read
            )
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        checkProtoBufConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        checkJsonConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, EmbeddedValuesDefinition.Model, { DefinitionsContext() })

        expect(
            """
            required: false
            final: true
            dataModel: MarykModel
            default:
              string: default

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, EmbeddedValuesDefinition.Model, { DefinitionsContext() })
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            EmbeddedValuesDefinition(
                dataModel = { MarykModel }
            ).compatibleWith(def)
        }

        assertFalse {
            EmbeddedValuesDefinition(
                dataModel = { TestMarykModel }
            ).compatibleWith(def)
        }
    }
}
