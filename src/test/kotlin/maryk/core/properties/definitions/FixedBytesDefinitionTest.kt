package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class FixedBytesDefinitionTest {
    private val fixedBytesToTest = arrayOf(
            Bytes(ByteArray(5, { 0x00.toByte() } )),
            Bytes(ByteArray(5, { 0xFF.toByte() } )),
            Bytes(ByteArray(5, { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() } ))
    )

    val def = FixedBytesDefinition(
            name = "test",
            byteSize = 5
    )

    @Test
    fun createRandom() {
        def.createRandom()
    }

    @Test
    fun testStorageConversion() {
        val byteCollector = ByteCollector()
        fixedBytesToTest.forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        fixedBytesToTest.forEach { value ->
            def.writeTransportBytesWithKey(value, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe -1
            def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun convertToString() {
        fixedBytesToTest.forEach {
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