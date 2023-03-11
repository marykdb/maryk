package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class BooleanDefinitionTest {
    val def = BooleanDefinition()
    val defMaxDefined = BooleanDefinition(
        required = false,
        final = true,
        default = true
    )

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (bool in booleanArrayOf(true, false)) {
            bc.reserve(
                def.calculateStorageByteLength(bool)
            )
            def.writeStorageBytes(bool, bc::write)
            expect(bool) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (boolean in booleanArrayOf(true, false)) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(23, boolean, cacheFailer, null)
            )
            def.writeTransportBytesWithKey(23, boolean, cacheFailer, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            expect(23u) { key.tag }
            expect(VAR_INT) { key.wireType }
            expect(boolean) {
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
        for (bool in booleanArrayOf(true, false)) {
            val b = def.asString(bool)
            expect(bool) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, BooleanDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, BooleanDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, BooleanDefinition.Model.Model)
        checkJsonConversion(this.defMaxDefined, BooleanDefinition.Model.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, BooleanDefinition.Model.Model)

        expect(
            """
            required: false
            final: true
            default: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, BooleanDefinition.Model.Model)
        }
    }
}
