package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class BooleanDefinitionTest {
    val def = BooleanDefinition()
    val defMaxDefined = BooleanDefinition(
            indexed = true,
            required = false,
            final = true,
            searchable = false
    )

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        booleanArrayOf(true, false).forEach {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        booleanArrayOf(true, false).forEach {
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
    fun `convert values to String and back`() {
        booleanArrayOf(true, false).forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun `invalid String value should throw exception`() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, BooleanDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, BooleanDefinition.Model)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, BooleanDefinition.Model)
        checkJsonConversion(this.defMaxDefined, BooleanDefinition.Model)
    }
}