package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.extensions.bytes.ZEROBYTE
import maryk.core.properties.ByteCollector
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class ReferenceDefinitionTest {
    private val refToTest = arrayOf(
            Key<TestMarykObject>(ByteArray(9, { ZEROBYTE })),
            Key<TestMarykObject>(ByteArray(9, { MAXBYTE })),
            Key<TestMarykObject>(ByteArray(9, { if (it % 2 == 1) 0b1000_1000.toByte() else MAXBYTE }))
    )

    val def = ReferenceDefinition(
            name = "test",
            index = 8,
            dataModel = TestMarykObject
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestMarykObject
    }

    @Test
    fun convertToString() {
        refToTest.forEach {
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

    @Test
    fun testStorageConversion() {
        val byteCollector = ByteCollector()
        refToTest.forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        refToTest.forEach { value ->
            def.writeTransportBytesWithKey(value, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 8
            def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.reset()
        }
    }
}