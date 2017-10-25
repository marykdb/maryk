package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollectorWithSizeCacher
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test
import kotlin.test.assertTrue

internal class ListDefinitionTest {
    val subDef = StringDefinition(
            required = true,
            regEx = "T.*"
    )

    val def = ListDefinition<String>(
            index = 3,
            name = "stringList",
            minSize = 2,
            maxSize = 4,
            required = true,
            valueDefinition = subDef
    )

    val def2 = ListDefinition<String>(
            name = "stringList2",
            minSize = 2,
            maxSize = 4,
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
        def.validate(newValue = listOf("T", "T2"))
        def.validate(newValue = listOf("T", "T2", "T3"))
        def.validate(newValue = listOf("T", "T2", "T3", "T4"))

        shouldThrow<PropertyTooLittleItemsException> {
            def.validate(newValue = listOf("T"))
        }

        shouldThrow<PropertyTooMuchItemsException> {
            def.validate(newValue = listOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun testValidateContent() {
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = listOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        with(e.exceptions[0]) {
            assertTrue(this is PropertyInvalidValueException)
            this.reference!!.completeName shouldBe "stringList[1]"
        }
        with(e.exceptions[1]) {
            assertTrue(this is PropertyInvalidValueException)
            this.reference!!.completeName shouldBe "stringList[2]"
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithSizeCacher()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "1a01541a0254321a0254331a025434"

        bc.reserve(
                def.reserveTransportBytesWithKey(value, bc::addToCache)
        )
        def.writeTransportBytesWithKey(value, bc::nextSizeFromCache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 3
        }

        fun readValue() = def.readCollectionTransportBytes(
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read
        )

        value.forEach {
            readKey()
            readValue() shouldBe it
        }
    }
}