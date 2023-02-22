package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

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
        minSize = 3u,
        maxSize = 6u
    )
    private val defMaxDefined = StringDefinition(
        required = false,
        final = true,
        unique = true,
        minSize = 3u,
        maxSize = 6u,
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

        assertFailsWith<InvalidSizeException> {
            def.validateWithRef(newValue = "ab")
        }
        assertFailsWith<InvalidSizeException> {
            def.validateWithRef(newValue = "abcdefg")
        }
    }

    @Test
    fun validateValuesWithRegularExpression() {
        // Should succeed
        defRegEx.validateWithRef(newValue = "abc")

        assertFailsWith<InvalidValueException> {
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
            expect(value) { def.readStorageBytes(bc.size, bc::read) }
            expect(asHex) { bc.bytes!!.toHex() }
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
            expect(value.calculateUTF8ByteLength() + 2) { bc.bytes!!.size }
            def.writeTransportBytesWithKey(14, value, cache, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(14u) { key.tag }
            expect(value) {
                def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, bc::read),
                    bc::read
                )
            }
            assertTrue { bc.bytes!!.toHex().endsWith(asHex) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (string in stringsToTest.keys) {
            val b = def.asString(string)
            expect(string) { def.fromString(b) }
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

        expect(
            """
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
        ) {
            checkYamlConversion(this.defMaxDefined, StringDefinition.Model)
        }
    }

    @Test
    fun convertToTransportByteArray() {
        for ((string, hex) in stringsToTest) {
            assertEquals(hex, def.toTransportByteArray(string).toHex())
        }
    }

    @Test
    fun isCompatible() {
        // maxSize
        assertTrue {
            StringDefinition().compatibleWith(StringDefinition(maxSize = 4u))
        }

        assertTrue {
            StringDefinition(maxSize = 5u).compatibleWith(StringDefinition(maxSize = 4u))
        }

        assertFalse {
            StringDefinition(maxSize = 5u).compatibleWith(StringDefinition())
        }

        assertFalse {
            StringDefinition(maxSize = 5u).compatibleWith(StringDefinition(maxSize = 6u))
        }

        // minSize
        assertTrue {
            StringDefinition().compatibleWith(StringDefinition(minSize = 4u))
        }

        assertTrue {
            StringDefinition(minSize = 5u).compatibleWith(StringDefinition(minSize = 6u))
        }

        assertFalse {
            StringDefinition(minSize = 5u).compatibleWith(StringDefinition())
        }

        assertFalse {
            StringDefinition(minSize = 7u).compatibleWith(StringDefinition(minSize = 6u))
        }

        // regEx
        assertTrue {
            StringDefinition().compatibleWith(StringDefinition(regEx = "[abc]{1,3}"))
        }

        assertTrue {
            StringDefinition(regEx = "[abc]{1,3}").compatibleWith(StringDefinition(regEx = "[abc]{1,3}"))
        }

        assertFalse {
            StringDefinition(regEx = "[def]{1,3}").compatibleWith(StringDefinition(regEx = "[abc]{1,3}"))
        }

        assertFalse {
            StringDefinition(regEx = "[abc]{1,3}").compatibleWith(StringDefinition())
        }
    }
}

