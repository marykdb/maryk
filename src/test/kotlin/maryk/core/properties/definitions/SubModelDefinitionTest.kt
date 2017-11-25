package maryk.core.properties.definitions

import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.toHex
import maryk.core.objects.DataModel
import maryk.core.objects.Def
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class SubModelDefinitionTest {
    private data class MarykObject(
            val string: String = "jur"
    ){
        object Properties {
            val string = StringDefinition(
                    name = "string",
                    index = 0,
                    regEx = "jur"
            )
        }
        companion object: DataModel<MarykObject, IsPropertyContext>(
                construct = { MarykObject(it[0] as String)},
                definitions = listOf(
                Def(Properties.string, MarykObject::string)
        ))
    }

    private val def = SubModelDefinition(
            name = "test",
            index = 1,
            dataModel = MarykObject
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe MarykObject
    }

    @Test
    fun validate() {
        def.validate(newValue = MarykObject())
        shouldThrow<ValidationUmbrellaException> {
            def.validate(newValue = MarykObject("wrong"))
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = MarykObject()
        val asHex = "2a0502036a7572"

        bc.reserve(
                def.calculateTransportByteLengthWithKey(value, bc::addToCache)
        )
        bc.bytes!!.size shouldBe 7
        def.writeTransportBytesWithIndexKey(5, value, bc::nextLengthFromCache, bc::write, null)

        bc.bytes!!.toHex() shouldBe asHex

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe 5

        def.readTransportBytes(
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read
        ) shouldBe value
    }

    @Test
    fun testTransportWithLengthConversion() {
        val value = MarykObject()
        val bytes = initByteArrayByHex("0a0502036a7572")
        var index = 0
        val reader = { bytes[index++] }

        val key = ProtoBuf.readKey(reader)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe 1

        def.readTransportBytes(
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, reader),
                reader
        ) shouldBe value
    }
}