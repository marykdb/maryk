package maryk.core.properties.definitions.contextual

import maryk.TestMarykObject
import maryk.core.extensions.toHex
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.wrapper.DataObjectProperty
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.query.DataModelPropertyContext
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
            reference = TestMarykObject.Properties.listOfString.getRef() as IsPropertyReference<*, DataObjectProperty<*, *, *, *>>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "ea020154ea02025432ea02025433ea02025434"

        bc.reserve(
                def.calculateTransportByteLengthWithKey(45, value, bc::addToCache, this.context)
        )
        def.writeTransportBytesWithKey(45, value, bc::nextLengthFromCache, bc::write, this.context)

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