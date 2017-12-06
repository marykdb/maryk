package maryk.core.properties.definitions

import maryk.core.extensions.toHex
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.TooLittleItemsException
import maryk.core.properties.exceptions.TooMuchItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

internal class SetDefinitionTest {
    private val subDef = StringDefinition(
            regEx = "T.*",
            required = true
    )

    private val def = SetDefinition(
            minSize = 2,
            maxSize = 4,
            required = true,
            valueDefinition = subDef
    )

    private val def2 = SetDefinition(
            valueDefinition = subDef
    )

    @Test
    fun testValidateRequired() {
        def2.validateWithRef(newValue = null)

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun testValidateSize() {
        def.validateWithRef(newValue = setOf("T", "T2"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4"))

        shouldThrow<TooLittleItemsException> {
            def.validateWithRef(newValue = setOf("T"))
        }

        shouldThrow<TooMuchItemsException> {
            def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun testValidateContent() {
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = setOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        assertTrue(e.exceptions[0] is InvalidValueException)
        assertTrue(e.exceptions[1] is InvalidValueException)
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = setOf("T", "T2", "T3", "T4")
        val asHex = "220154220254322202543322025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(4, value, bc::addToCache)
        )
        def.writeTransportBytesWithKey(4, value, bc::nextLengthFromCache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 4
        }

        fun readValue() = def.readCollectionTransportBytes(
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read,
                null
        )

        value.forEach {
            readKey()
            readValue() shouldBe it
        }
    }

    @Test
    fun testJsonConversion() {
        val value = setOf("T", "T2", "T3", "T4")

        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        totalString shouldBe "[\"T\",\"T2\",\"T3\",\"T4\"]"

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        converted shouldBe value
    }
}