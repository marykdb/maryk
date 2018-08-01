package maryk.core.properties.definitions.contextual

import maryk.TestMarykModel
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelPropertyContext
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualCollectionDefinitionTest {
    private val listToTest = listOf(
        "T1", "T2", "T3"
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualCollectionDefinition<DataModelPropertyContext>(
        contextualResolver = { it!!.reference!!.propertyDefinition as IsByteTransportableCollection<Any, Collection<Any>, DataModelPropertyContext> }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(),
        reference = TestMarykModel.ref { listOfString } as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *, *>, *>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "ea020154ea02025432ea02025433ea02025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(45, value, cache, this.context)
        )
        def.writeTransportBytesWithKey(45, value, cache, bc::write, this.context)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 45
        }

        fun readValue() = def.readCollectionTransportBytes(
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
            bc::read,
            this.context
        )

        value.forEach {
            readKey()
            readValue() shouldBe it
        }
    }

    @Test
    fun convertJson() {
        var totalString = ""
        def.writeJsonValue(listToTest, JsonWriter { totalString += it }, this.context)

        totalString shouldBe "[\"T1\",\"T2\",\"T3\"]"

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader, this.context)

        converted shouldBe listToTest
    }
}
