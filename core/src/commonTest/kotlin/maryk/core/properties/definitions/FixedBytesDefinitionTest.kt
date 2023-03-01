package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.Bytes
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class FixedBytesDefinitionTest {
    private val fixedBytesToTest = arrayOf(
        Bytes(ByteArray(5) { 0x00.toByte() }),
        Bytes(ByteArray(5) { 0xFF.toByte() }),
        Bytes(ByteArray(5) { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() })
    )

    val def = FixedBytesDefinition(
        byteSize = 5
    )

    val defMaxDefined = FixedBytesDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = Bytes.ofHex("0000000000"),
        maxValue = Bytes.ofHex("AAAAAAAAAA"),
        byteSize = 5,
        default = Bytes.ofHex("0000000001")
    )

    @Test
    fun createRandomValue() {
        def.createRandom()
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (fixedBytes in fixedBytesToTest) {
            bc.reserve(
                def.calculateStorageByteLength(fixedBytes)
            )
            def.writeStorageBytes(fixedBytes, bc::write)
            expect(fixedBytes) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        for (it in fixedBytesToTest) {
            checkProtoBufConversion(bc, it, this.def)
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (fixedBytes in fixedBytesToTest) {
            val b = def.asString(fixedBytes)
            expect(fixedBytes) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong§")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, FixedBytesDefinition.Model.Model)
        checkProtoBufConversion(this.defMaxDefined, FixedBytesDefinition.Model.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, FixedBytesDefinition.Model.Model)
        checkJsonConversion(this.defMaxDefined, FixedBytesDefinition.Model.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, FixedBytesDefinition.Model.Model)

        expect(
            """
            required: false
            final: true
            unique: true
            minValue: AAAAAAA
            maxValue: qqqqqqo
            default: AAAAAAE
            byteSize: 5

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, FixedBytesDefinition.Model.Model)
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            FixedBytesDefinition(byteSize = 3).compatibleWith(FixedBytesDefinition(byteSize = 3))
        }

        assertFalse {
            FixedBytesDefinition(byteSize = 3).compatibleWith(FixedBytesDefinition(byteSize = 9))
        }
    }
}
