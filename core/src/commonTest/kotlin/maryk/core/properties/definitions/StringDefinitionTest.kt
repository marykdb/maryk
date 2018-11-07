package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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

    private val def = StringDefinition(
        minSize = 3,
        maxSize = 6
    )
    private val defMaxDefined = StringDefinition(
        indexed = true,
        required = false,
        final = true,
        unique = true,
        minSize = 3,
        maxSize = 6,
        default = "aaa",
        regEx = "^[abcd]{3,4}$",
        minValue = "aaa",
        maxValue = "zzzzz"
    )

    private val defRegEx = StringDefinition(
        regEx = "^[abcd]{3,4}$"
    )

    @Test
    fun validateValues() {
        // Should both succeed without errors
        def.validateWithRef(newValue = "abc")
        def.validateWithRef(newValue = "abcdef")

        shouldThrow<InvalidSizeException> {
            def.validateWithRef(newValue = "ab")
        }
        shouldThrow<InvalidSizeException> {
            def.validateWithRef(newValue = "abcdefg")
        }
    }

    @Test
    fun validateValuesWithRegularExpression() {
        // Should succeed
        defRegEx.validateWithRef(newValue = "abc")

        shouldThrow<InvalidValueException> {
            defRegEx.validateWithRef(newValue = "efgh")
        }
    }

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for ((value, asHex) in stringsToTest) {
            bc.reserve(
                def.calculateStorageByteLength(value)
            )
            def.writeStorageBytes(value, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe value
            bc.bytes!!.toHex() shouldBe asHex
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()
        for ((value, asHex) in stringsToTest) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(14, value, cache)
            )
            bc.bytes!!.size shouldBe value.calculateUTF8ByteLength() + 2
            def.writeTransportBytesWithKey(14, value, cache, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 14
            def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read
            ) shouldBe value
            bc.bytes!!.toHex().endsWith(asHex) shouldBe true
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (it in stringsToTest.keys) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, StringDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, StringDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, StringDefinition.Model)
        checkJsonConversion(this.defMaxDefined, StringDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, StringDefinition.Model)
        checkYamlConversion(this.defMaxDefined, StringDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        unique: true
        minValue: aaa
        maxValue: zzzzz
        default: aaa
        minSize: 3
        maxSize: 6
        regEx: ^[abcd]{3,4}${'$'}

        """.trimIndent()
    }
}

