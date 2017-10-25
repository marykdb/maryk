package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.extensions.bytes.ZEROBYTE
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithSizeCacher
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
    fun convertString() {
        refToTest.forEach {
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

    @Test
    fun testStorageConversion() {
        val byteCollector = ByteCollector()
        refToTest.forEach {
            def.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithSizeCacher()
        refToTest.forEach { value ->
            bc.reserve(
                def.calculateTransportByteLengthWithKey(value, bc::addToCache)
            )
            def.writeTransportBytesWithKey(value, bc::nextSizeFromCache, bc::write)
            bc.bytes!!.size shouldBe 11

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