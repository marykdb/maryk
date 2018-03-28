package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.extensions.toHex
import maryk.core.objects.DataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelContext
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
            name = "MarykObject",
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
    private val defMaxDefined = SubModelDefinition(
        indexed = true,
        required = false,
        final = true,
        searchable = false,
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
    fun convert_values_to_transport_bytes_and_back() {
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

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, SubModelDefinition.Model, DataModelContext())
        checkProtoBufConversion(this.defMaxDefined, SubModelDefinition.Model, DataModelContext())
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, SubModelDefinition.Model, DataModelContext())
        checkJsonConversion(this.defMaxDefined, SubModelDefinition.Model, DataModelContext())
    }
}