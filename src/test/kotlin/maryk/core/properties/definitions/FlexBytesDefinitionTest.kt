package maryk.core.properties.definitions

import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.types.Bytes
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class FlexBytesDefinitionTest {
    private val flexBytesToTest = arrayOf(
            Bytes(ByteArray(5, { 0x00.toByte() } )),
            Bytes(ByteArray(7, { 0xFF.toByte() } )),
            Bytes(ByteArray(9, { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() } ))
    )

    val def = FlexBytesDefinition(
            name = "test",
            minSize = 4,
            maxSize = 10
    )

    @Test
    fun validate() {
        // Should both succeed without errors
        def.validate(newValue = Bytes(ByteArray(4, { 0x00.toByte() } )))
        def.validate(newValue = Bytes(ByteArray(5, { 0x00.toByte() } )))
        def.validate(newValue = Bytes(ByteArray(10, { 0x00.toByte() } )))

        shouldThrow<InvalidSizeException> {
            def.validate(newValue = Bytes(ByteArray(1, { 0x00.toByte() } )))
        }
        shouldThrow<InvalidSizeException> {
            def.validate(newValue = Bytes(ByteArray(20, { 0x00.toByte() } )))
        }
    }

    @Test
    fun testStorageConversion() {
        val bc = ByteCollector()
        flexBytesToTest.forEach {
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
        val bc = ByteCollectorWithLengthCacher()
        flexBytesToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun convertToString() {
        flexBytesToTest.forEach {
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