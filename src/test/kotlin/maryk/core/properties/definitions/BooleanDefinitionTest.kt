package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class BooleanDefinitionTest {
    val def = BooleanDefinition(
            name = "test",
            index = 222
    )

    @Test
    fun convertStorageBytes() {
        val byteCollector = ByteCollector()
        booleanArrayOf(true, false).forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        booleanArrayOf(true, false).forEach {
            def.writeTransportBytesWithKey(it, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 222
            key.wireType shouldBe WireType.VAR_INT
            def.readTransportBytes(
                    ProtoBuf.getLength(WireType.VAR_INT, bc::read),
                    bc::read
            ) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertToString() {
        booleanArrayOf(true, false).forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong")
        }
    }
}