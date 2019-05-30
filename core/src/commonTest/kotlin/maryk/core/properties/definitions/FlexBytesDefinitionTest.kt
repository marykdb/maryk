package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.types.Bytes
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class FlexBytesDefinitionTest {
    private val flexBytesToTest = arrayOf(
        Bytes(ByteArray(5) { 0x00.toByte() }),
        Bytes(ByteArray(7) { 0xFF.toByte() }),
        Bytes(ByteArray(9) { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() })
    )

    val def = FlexBytesDefinition(
        minSize = 4u,
        maxSize = 10u
    )

    val defMaxDefined = FlexBytesDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = Bytes.ofHex("0000000000"),
        maxValue = Bytes.ofHex("AAAAAAAAAA"),
        minSize = 4u,
        maxSize = 10u,
        default = Bytes.ofHex("0000000001")
    )

    @Test
    fun validateValues() {
        // Should both succeed without errors
        def.validateWithRef(newValue = Bytes(ByteArray(4) { 0x00.toByte() }))
        def.validateWithRef(newValue = Bytes(ByteArray(5) { 0x00.toByte() }))
        def.validateWithRef(newValue = Bytes(ByteArray(10) { 0x00.toByte() }))

        assertFailsWith<InvalidSizeException> {
            def.validateWithRef(newValue = Bytes(ByteArray(1) { 0x00.toByte() }))
        }
        assertFailsWith<InvalidSizeException> {
            def.validateWithRef(newValue = Bytes(ByteArray(20) { 0x00.toByte() }))
        }
    }

    @Test
    fun convertJSONToDataObjectvaluesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (flexBytes in flexBytesToTest) {
            bc.reserve(
                def.calculateStorageByteLength(flexBytes)
            )
            def.writeStorageBytes(flexBytes, bc::write)
            expect(flexBytes) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        for (it in flexBytesToTest) {
            checkProtoBufConversion(bc, it, this.def)
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (flexBytes in flexBytesToTest) {
            val b = def.asString(flexBytes)
            expect(flexBytes) { def.fromString(b) }
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
        checkProtoBufConversion(this.def, FlexBytesDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, FlexBytesDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, FlexBytesDefinition.Model)
        checkJsonConversion(this.defMaxDefined, FlexBytesDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, FlexBytesDefinition.Model)

        expect(
            """
            required: false
            final: true
            unique: true
            minValue: AAAAAAA
            maxValue: qqqqqqo
            default: AAAAAAE
            minSize: 4
            maxSize: 10

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, FlexBytesDefinition.Model)
        }
    }
}
