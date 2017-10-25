package maryk.core.properties.definitions

import io.kotlintest.matchers.fail
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.Option
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
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
            def.writeStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.readStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
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
                    def.calculateTransportByteLengthWithKey(enum, { fail("Should not call") })
            )
            def.writeTransportBytesWithKey(enum, { fail("Should not call") }, bc::write)
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