package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class BooleanDefinitionTest {
    val def = BooleanDefinition()
    val defMaxDefined = BooleanDefinition(
        indexed = true,
        required = false,
        final = true,
        default = true
    )

    @Test
    fun convert_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        for (it in booleanArrayOf(true, false)) {
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
        val cacheFailer = WriteCacheFailer()

        for (it in booleanArrayOf(true, false)) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(23, it, cacheFailer, null)
            )
            def.writeTransportBytesWithKey(23, it, cacheFailer, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 23
            key.wireType shouldBe WireType.VAR_INT
            def.readTransportBytes(
                ProtoBuf.getLength(WireType.VAR_INT, bc::read),
                bc::read,
                null
            ) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convert_values_to_String_and_back() {
        for (it in booleanArrayOf(true, false)) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalid_String_value_should_throw_exception() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, BooleanDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, BooleanDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, BooleanDefinition.Model)
        checkJsonConversion(this.defMaxDefined, BooleanDefinition.Model)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, BooleanDefinition.Model)
        checkYamlConversion(this.defMaxDefined, BooleanDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        default: true

        """.trimIndent()
    }
}