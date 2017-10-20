package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class NumberDefinitionTest {
    private val def = NumberDefinition(
            name = "test",
            type = UInt32
    )

    private val intArray = arrayOf(
            UInt32.MIN_VALUE,
            UInt32.MAX_VALUE,
            32373957.toUInt32()
    )

    @Test
    fun hasValues() {
        def.type shouldBe UInt32
    }

    @Test
    fun createRandom() {
        def.createRandom()
    }

    @Test
    fun convertStorageBytes() {
        val byteCollector = ByteCollector()
        intArray.forEach {
            def.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        intArray.forEach { value ->
            def.writeTransportBytesWithKey(value, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.VAR_INT
            key.tag shouldBe -1
            def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun convertString() {
        intArray.forEach {
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