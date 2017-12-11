package maryk.core.properties.definitions

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
            Option.V0,
            Option.V1
    )

    val def = EnumDefinition(
            values = Option.values()
    )

    val defMaxDefined = EnumDefinition(
            indexed = true,
            required = false,
            final = true,
            searchable = false,
            unique = true,
            minValue = Option.V0,
            maxValue = Option.V2,
            values = Option.values()
    )

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        enumsToTest.forEach {
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

        val expected = arrayOf(
                "7000",
                "7001"
        )

        enumsToTest.zip(expected).forEach { (enum, expected) ->
            bc.reserve(
                    def.calculateTransportByteLengthWithKey(14, enum, cacheFailer, null)
            )
            def.writeTransportBytesWithKey(14, enum, cacheFailer, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 14
            key.wireType shouldBe WireType.VAR_INT

            bc.bytes!!.toHex() shouldBe expected

            def.readTransportBytes(
                    ProtoBuf.getLength(WireType.VAR_INT, bc::read),
                    bc::read
            ) shouldBe enum
            bc.reset()
        }
    }

    @Test
    fun `convert values to String and back`() {
        enumsToTest.forEach {
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
        checkProtoBufConversion(this.def, EnumDefinition, null, ::compare)
        checkProtoBufConversion(this.defMaxDefined, EnumDefinition, null, ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, EnumDefinition, null, ::compare)
        checkJsonConversion(this.defMaxDefined, EnumDefinition, null, ::compare)
    }
}

private fun compare(converted: EnumDefinition<*>, original: EnumDefinition<*>) {
    converted shouldBe original
    converted.hashCode() shouldBe original.hashCode()
}