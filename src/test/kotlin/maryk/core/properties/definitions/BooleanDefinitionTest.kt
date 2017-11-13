package maryk.core.properties.definitions

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.fail

internal class BooleanDefinitionTest {
    val def = BooleanDefinition(
            name = "test",
            index = 222
    )

    @Test
    fun testStorageConversion() {
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
    fun testTransportConversion() {
        val bc = ByteCollector()
        booleanArrayOf(true, false).forEach {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(it, { fail("Should not call") }, null)
            )
            def.writeTransportBytesWithKey(it, { fail("Should not call") }, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 222
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
    fun testStringConversion() {
        booleanArrayOf(true, false).forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun testWrongStringConversion() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}