package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.Option
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
            Option.V0,
            Option.V1
    )

    val def = EnumDefinition<Option>(
            name = "enum",
            index = 14,
            values = Option.values()
    )

    @Test
    fun convertStorageBytes() {
        val byteCollector = ByteCollector()
        enumsToTest.forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()

        val expected = arrayOf(
                "7000",
                "7001"
        )

        enumsToTest.zip(expected).forEach { (enum, expected) ->
            def.writeTransportBytesWithKey(enum, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 14
            key.wireType shouldBe WireType.VAR_INT

            bc.bytes.toHex() shouldBe expected

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