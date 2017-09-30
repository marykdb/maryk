package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.types.Bytes
import org.junit.Test

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

        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = Bytes(ByteArray(1, { 0x00.toByte() } )))
        }
        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = Bytes(ByteArray(20, { 0x00.toByte() } )))
        }
    }

    @Test
    fun testStreamingConversion() {
        val byteCollector = ByteCollector()
        flexBytesToTest.forEach {
            def.convertToStorageBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }

    @Test
    fun convertToString() {
        flexBytesToTest.forEach {
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