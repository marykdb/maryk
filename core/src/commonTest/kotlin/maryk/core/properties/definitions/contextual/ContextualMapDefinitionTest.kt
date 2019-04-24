package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.KeyValueDefinitionContext
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualMapDefinitionTest {
    @Suppress("UNCHECKED_CAST")
    private val mapToTest = mapOf(
        Time(1, 55, 33) to "hello",
        Time(14, 22, 23) to "goodBye"
    ) as Map<Any, Any>

    private val def = ContextualMapDefinition<Any, Any, KeyValueDefinitionContext>(
        contextualResolver = { context.mapDefinition }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = KeyValueDefinitionContext(
        definitionsContext = null,
        keyDefinition = TestMarykModel.properties.map.keyDefinition as IsSimpleValueDefinition<Any, IsPropertyContext>,
        valueDefinition = TestMarykModel.properties.map.valueDefinition as IsValueDefinition<Any, IsPropertyContext>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = mapToTest

        bc.reserve(
            def.calculateTransportByteLengthWithKey(8u, value, cache, this.context)
        )
        def.writeTransportBytesWithKey(8u, value, cache, bc::write, this.context)

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe LENGTH_DELIMITED
            key.tag shouldBe 8u
        }

        fun readValue(): Pair<Any, Any> {
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read)
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
