package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.types.Bytes
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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
        indexed = true,
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

        shouldThrow<InvalidSizeException> {
            def.validateWithRef(newValue = Bytes(ByteArray(1) { 0x00.toByte() }))
        }
        shouldThrow<InvalidSizeException> {
            def.validateWithRef(newValue = Bytes(ByteArray(20) { 0x00.toByte() }))
        }
    }

    @Test
    fun convertJSONToDataObjectvaluesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (it in flexBytesToTest) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
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
        for (it in flexBytesToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        shouldThrow<ParseException> {
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
        checkYamlConversion(this.defMaxDefined, FlexBytesDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        unique: true
        minValue: AAAAAAA
        maxValue: qqqqqqo
        default: AAAAAAE
        minSize: 4
        maxSize: 10

        """.trimIndent()
    }
}
