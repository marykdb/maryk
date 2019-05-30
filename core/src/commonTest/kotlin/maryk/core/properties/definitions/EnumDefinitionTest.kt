package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.query.DefinitionsContext
import maryk.core.yaml.MarykYamlReaders
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.Option
import maryk.test.models.Option.UnknownOption
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
        V1,
        V2,
        UnknownOption(99u, "%Unknown")
    )

    val def = EnumDefinition(
        enum = Option
    )

    val defMaxDefined = EnumDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = V1,
        maxValue = V3,
        enum = Option,
        default = V2
    )

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (enum in enumsToTest) {
            bc.reserve(
                def.calculateStorageByteLength(enum)
            )
            def.writeStorageBytes(enum, bc::write)
            expect(enum) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        val expectedEnums = arrayOf(
            "7001",
            "7002"
        )

        for ((enum, expected) in enumsToTest.zip(expectedEnums)) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(14u, enum, cacheFailer, null)
            )
            def.writeTransportBytesWithKey(14u, enum, cacheFailer, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            expect(14u) { key.tag }
            expect(VAR_INT) { key.wireType }

            expect(expected) { bc.bytes!!.toHex() }

            expect(enum) {
                def.readTransportBytes(
                    ProtoBuf.getLength(VAR_INT, bc::read),
                    bc::read
                )
            }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (enum in enumsToTest) {
            val b = def.asString(enum)
            expect(enum) { def.fromString(b) }
        }
    }

    @Test
    fun convertValuesFromAlternativeStrings() {
        expect(V2) { def.fromString("VERSION2") }
        expect(V3) { def.fromString("VERSION3") }
    }

    @Test
    fun invalidStringValueShouldReturnUnknown() {
        expect(UnknownOption(0u, "wrong")) {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, EnumDefinition.Model, null, ::compare)
        checkProtoBufConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, EnumDefinition.Model, null, ::compare)
        checkJsonConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, EnumDefinition.Model, null, ::compare)

        expect(
            """
            required: false
            final: true
            unique: true
            enum:
              name: Option
              cases:
                1: V1
                2: [V2, VERSION2]
                3: [V3, VERSION3]
              reservedIndices: [4]
              reservedNames: [V4]
            minValue: V1(1)
            maxValue: V3(3)
            default: V2(2)

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare)
        }
    }

    @Test
    fun convertDefinitionFromYaml() {
        val chars = """
        enum: Option
        minValue: V1(1)
        maxValue: V3(3)
        default: V2(2)
        """.iterator()

        val reader = MarykYamlReaders {
            chars.nextChar().also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        val context = EnumDefinition.Model.transformContext(
            DefinitionsContext(
                enums = mutableMapOf(
                    "Option" to Option as IndexedEnumDefinition<IndexedEnum>
                )
            )
        )

        expect(
            EnumDefinition(
                enum = Option,
                minValue = V1,
                maxValue = V3,
                default = V2
            )
        ) {
            EnumDefinition.Model.readJson(reader, context).toDataObject()
        }
    }
}

private fun compare(converted: EnumDefinition<*>, original: EnumDefinition<*>) {
    assertEquals(original, converted)
    assertEquals(original.hashCode(), converted.hashCode())
}
