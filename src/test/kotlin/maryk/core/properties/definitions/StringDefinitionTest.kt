package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.extensions.toHex
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class StringDefinitionTest {

    private val stringsToTest = mapOf(
            "" to "",
            "test123!@#éüî[]{}" to "74657374313233214023c3a9c3bcc3ae5b5d7b7d",
            "汉语/漢語" to "e6b189e8afad2fe6bca2e8aa9e",
            "العَرَبِيَّة" to "d8a7d984d8b9d98ed8b1d98ed8a8d990d98ad98ed991d8a9",
            "עִבְרִית" to "d7a2d6b4d791d6b0d7a8d6b4d799d7aa",
            "한국어" to "ed959ceab5adec96b4",
            "日本語" to "e697a5e69cace8aa9e",
            "ελληνικά" to "ceb5cebbcebbceb7cebdceb9cebaceac",
            "ฉันฟังไม่เข้าใจ" to "e0b889e0b8b1e0b899e0b89fe0b8b1e0b887e0b984e0b8a1e0b988e0b980e0b882e0b989e0b8b2e0b983e0b888",
            "👩‍💻" to "f09f91a9e2808df09f92bb"
    )

    val def = StringDefinition(
            index = 14,
            minSize = 3,
            maxSize = 6,
            name = "test"
    )

    val defRegEx = StringDefinition(
            name = "test",
            regEx = "^[abcd]{3,4}$"
    )

    @Test
    fun validate() {
        // Should both succeed without errors
        def.validate(newValue = "abc")
        def.validate(newValue = "abcdef")

        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = "ab")
        }
        shouldThrow<PropertyInvalidSizeException> {
            def.validate(newValue = "abcdefg")
        }
    }

    @Test
    fun validateRegex() {
        // Should succeed
        defRegEx.validate(newValue = "abc")

        shouldThrow<PropertyInvalidValueException> {
            defRegEx.validate(newValue = "efgh")
        }
    }

    @Test
    fun convertStorageBytes() {
        val byteCollector = GrowableByteCollector()
        stringsToTest.forEach { (value, asHex) ->
            def.convertToStorageBytes(value, byteCollector::reserve, byteCollector::write)
            def.convertFromStorageBytes(byteCollector.size, byteCollector::read) shouldBe value
            byteCollector.bytes.toHex() shouldBe asHex
            byteCollector.reset()
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        stringsToTest.forEach { (value, asHex) ->
            def.writeTransportBytesWithKey(value, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 14
            def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
            ) shouldBe value
            bc.bytes.toHex().endsWith(asHex) shouldBe true
            bc.reset()
        }
    }

    @Test
    fun convertToString() {
        stringsToTest.keys.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }
}

