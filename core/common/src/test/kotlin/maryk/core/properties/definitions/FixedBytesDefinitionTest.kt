package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.Bytes
import maryk.lib.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class FixedBytesDefinitionTest {
    private val fixedBytesToTest = arrayOf(
        Bytes(ByteArray(5, { 0x00.toByte() } )),
        Bytes(ByteArray(5, { 0xFF.toByte() } )),
        Bytes(ByteArray(5, { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() } ))
    )

    val def = FixedBytesDefinition(
        byteSize = 5
    )

    val defMaxDefined = FixedBytesDefinition(
        indexed = true,
        required = false,
        final = true,
        searchable = false,
        unique = true,
        minValue = Bytes.ofHex("0000000000"),
        maxValue = Bytes.ofHex("AAAAAAAAAA"),
        random = true,
        byteSize = 5,
        default = Bytes.ofHex("0000000001")
    )

    @Test
    fun create_random_value() {
        def.createRandom()
    }

    @Test
    fun convert_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        for (it in fixedBytesToTest) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        for (it in fixedBytesToTest) {
            checkProtoBufConversion(bc, it, this.def)
        }
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in fixedBytesToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong§")
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, FixedBytesDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, FixedBytesDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, FixedBytesDefinition.Model)
        checkJsonConversion(this.defMaxDefined, FixedBytesDefinition.Model)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, FixedBytesDefinition.Model)
        checkYamlConversion(this.defMaxDefined, FixedBytesDefinition.Model) shouldBe """
        indexed: true
        searchable: false
        required: false
        final: true
        unique: true
        minValue: AAAAAAA
        maxValue: qqqqqqo
        default: AAAAAAE
        random: true
        byteSize: 5

        """.trimIndent()
    }
}
