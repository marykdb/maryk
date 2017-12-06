package maryk.core.properties.definitions

import maryk.Option
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.fail

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
            Option.V0,
            Option.V1
    )

    val def = EnumDefinition(
            values = Option.values()
    )

    @Test
    fun convertStorageBytes() {
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
    fun testTransportConversion() {
        val bc = ByteCollector()

        val expected = arrayOf(
                "7000",
                "7001"
        )

        enumsToTest.zip(expected).forEach { (enum, expected) ->
            bc.reserve(
                    def.calculateTransportByteLengthWithKey(14, enum, { fail("Should not call") }, null)
            )
            def.writeTransportBytesWithKey(14, enum, { fail("Should not call") }, bc::write, null)
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
    fun convertString() {
        enumsToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}