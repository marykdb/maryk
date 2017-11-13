package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

internal class ListDefinitionTest {
    private val subDef = StringDefinition(
            required = true,
            regEx = "T.*"
    )

    private val def = ListDefinition(
            index = 3,
            name = "stringList",
            minSize = 2,
            maxSize = 4,
            required = true,
            valueDefinition = subDef
    )

    private val def2 = ListDefinition(
            name = "stringList2",
            minSize = 2,
            maxSize = 4,
            valueDefinition = subDef
    )

    private val defVarInt = ListDefinition(
            name = "varIntList",
            index = 5,
            valueDefinition = NumberDefinition(type = UInt32, required = true)
    )

    private val def64Int = ListDefinition(
            name = "64IntList",
            index = 6,
            valueDefinition = NumberDefinition(type = Float64, required = true)
    )

    private val def32Int = ListDefinition(
            name = "32IntList",
            index = 7,
            valueDefinition = NumberDefinition(type = Float32, required = true)
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
            this.reference!!.completeName shouldBe "stringList.@1"
        }
        with(e.exceptions[1]) {
            assertTrue(this is PropertyInvalidValueException)
            this.reference!!.completeName shouldBe "stringList.@2"
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "1a01541a0254321a0254331a025434"

        bc.reserve(
                def.calculateTransportByteLengthWithKey(value, bc::addToCache)
        )
        def.writeTransportBytesWithKey(value, bc::nextLengthFromCache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 3
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

    @Test
    fun testTransportVarIntConversion() {
        val value = listOf(
                76523.toUInt32(),
                2423.toUInt32(),
                25423.toUInt32(),
                42.toUInt32()
        )
        val asHex = "2a09ebd504f712cfc6012a"

        this.testPackedTransportConversion(defVarInt, value, asHex, 5)
    }

    @Test
    fun testTransport32BitConversion() {
        val value = listOf(
                3.566F,
                58253.87652F,
                0.000222F,
                236453165416F
        )
        val asHex = "3a104064395947638de13968c8ad525c36d5"

        this.testPackedTransportConversion(def32Int, value, asHex, 7)
    }

    @Test
    fun testTransport64BitConversion() {
        val value = listOf(
                3.523874666,
                5825394671387643.87652,
                0.0002222222222,
                2364531654162343428.0
        )
        val asHex = "3220400c30e5336d62274334b22a641083fd3f2d208a5a84aba343c06840817d41b4"

        this.testPackedTransportConversion(def64Int, value, asHex, 6)
    }

    private fun <T: Any> testPackedTransportConversion(def: ListDefinition<T, *>, list: List<T>, hex: String, index: Int) {
        val bc = ByteCollectorWithLengthCacher()

        bc.reserve(
                def.calculateTransportByteLengthWithKey(list, bc::addToCache)
        )
        def.writeTransportBytesWithKey(list, bc::nextLengthFromCache, bc::write)

        bc.bytes!!.toHex() shouldBe hex

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe index


        val readList = def.readPackedCollectionTransportBytes(
                null,
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read
        )

        readList shouldBe list
    }
}