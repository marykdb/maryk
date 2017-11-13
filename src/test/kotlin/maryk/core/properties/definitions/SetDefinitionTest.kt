package maryk.core.properties.definitions

import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

internal class SetDefinitionTest {
    private val subDef = StringDefinition(
            name = "string",
            regEx = "T.*",
            required = true
    )

    private val def = SetDefinition(
            name = "stringSet",
            index = 4,
            minSize = 2,
            maxSize = 4,
            required = true,
            valueDefinition = subDef
    )

    private val def2 = SetDefinition(
            name = "stringSet",
            valueDefinition = subDef
    )

    @Test
    fun testValidateRequired() {
        def2.validate(newValue = null)

        shouldThrow<PropertyRequiredException> {
            def.validate(newValue = null)
        }
    }

    @Test
    fun testValidateSize() {
        def.validate(newValue = setOf("T", "T2"))
        def.validate(newValue = setOf("T", "T2", "T3"))
        def.validate(newValue = setOf("T", "T2", "T3", "T4"))

        shouldThrow<PropertyTooLittleItemsException> {
            def.validate(newValue = setOf("T"))
        }

        shouldThrow<PropertyTooMuchItemsException> {
            def.validate(newValue = setOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun testValidateContent() {
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = setOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        assertTrue(e.exceptions[0] is PropertyInvalidValueException)
        assertTrue(e.exceptions[1] is PropertyInvalidValueException)
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = setOf("T", "T2", "T3", "T4")
        val asHex = "220154220254322202543322025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(value, bc::addToCache)
        )
        def.writeTransportBytesWithKey(value, bc::nextLengthFromCache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 4
        }

        fun readValue() = def.readCollectionTransportBytes(
                null,
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read
        )

        value.forEach {
            readKey()
            readValue() shouldBe it
        }
    }
}