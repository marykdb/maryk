package maryk.core.properties.definitions.contextual

import maryk.TestMarykObject
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Time
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualMapDefinitionTest {
    @Suppress("UNCHECKED_CAST")
    private val mapToTest = mapOf(
                Time(1, 55,33) to "hello",
                Time(14, 22,23) to "goodBye"
    ) as Map<Any, Any>

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualMapDefinition<Any, Any, DataModelPropertyContext>(
            index = 8,
            name = "test",
            contextualResolver = { it!!.reference!!.propertyDefinition as IsByteTransportableMap<Any, Any, DataModelPropertyContext> }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(),
            reference = TestMarykObject.Properties.map.getRef() as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        val value = mapToTest

        bc.reserve(
                def.calculateTransportByteLengthWithKey(value, bc::addToCache, this.context)
        )
        def.writeTransportBytesWithKey(value, bc::nextLengthFromCache, bc::write, this.context)

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 8
        }

        fun readValue(): Pair<Any, Any> {
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read)
            return def.readMapTransportBytes(bc::read, this.context)
        }

        value.forEach {
            readKey()
            val mapValue = readValue()
            mapValue.first shouldBe it.key
            mapValue.second shouldBe it.value
        }
    }

    @Test
    fun convertJson() {
        var totalString = ""
        def.writeJsonValue(mapToTest, JsonWriter { totalString += it }, this.context)

        totalString shouldBe "{\"01:55:33\":\"hello\",\"14:22:23\":\"goodBye\"}"

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader, this.context)

        converted shouldBe this.mapToTest
    }
}