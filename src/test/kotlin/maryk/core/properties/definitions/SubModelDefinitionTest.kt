package maryk.core.properties.definitions

import maryk.core.extensions.toHex
import maryk.core.objects.DataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class SubModelDefinitionTest {
    private data class MarykObject(
            val string: String = "jur"
    ){
        object Properties : PropertyDefinitions<MarykObject>() {
            init {
                add(0, "string", StringDefinition(
                        regEx = "jur"
                ), MarykObject::string)
            }
        }
        companion object: DataModel<MarykObject, Properties>(
                properties = Properties
        ) {
            override fun invoke(map: Map<Int, *>) = MarykObject(
                    map[0] as String
            )
        }
    }

    private val def = SubModelDefinition(
            dataModel = { MarykObject }
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe MarykObject
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = MarykObject())
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = MarykObject("wrong"))
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = MarykObject()
        val asHex = "2a0502036a7572"

        bc.reserve(
                def.calculateTransportByteLengthWithKey(5, value, cache)
        )
        bc.bytes!!.size shouldBe 7
        def.writeTransportBytesWithKey(5, value, cache, bc::write, null)

        bc.bytes!!.toHex() shouldBe asHex

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe 5

        def.readTransportBytes(
                ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
                bc::read
        ) shouldBe value
    }
}