package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.NamedObjectModel
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class EmbeddedObjectDefinitionTest {
    private data class MarykObject(
        val string: String = "jur"
    ) {
        @Suppress("unused")
        companion object : NamedObjectModel<MarykObject, Companion>(MarykObject::class) {
            val string by string(
                1u,
                getter = MarykObject::string,
                regEx = "jur"
            )

            override fun invoke(values: ObjectValues<MarykObject, Companion>) = MarykObject(
                values(1u)
            )
        }
    }

    private val def = EmbeddedObjectDefinition(
        dataModel = { MarykObject }
    )
    private val defMaxDefined = EmbeddedObjectDefinition(
        required = false,
        final = true,
        dataModel = { MarykObject },
        default = MarykObject("default")
    )

    @Test
    fun hasValues() {
        expect(MarykObject) { def.dataModel }
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = MarykObject())
        assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = MarykObject("wrong"))
        }
    }

    @Test
    fun convertObjectToJSONAndBack() {
        val value = MarykObject()

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

        val value = MarykObject()

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

        expect(
            """
            required: false
            final: true
            dataModel: MarykObject
            default:
              string: default

            """.trimIndent()
        ) {
            checkYamlConversion(
                this.defMaxDefined,
                EmbeddedObjectDefinition.Model,
                { DefinitionsConversionContext() }
            )
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            EmbeddedObjectDefinition(
                dataModel = { MarykObject }
            ).compatibleWith(def)
        }

        assertFalse {
            EmbeddedObjectDefinition(
                dataModel = { TestMarykObject }
            ).compatibleWith(def)
        }
    }
}
