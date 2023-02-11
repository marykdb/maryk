package maryk.core.properties.definitions.contextual

import kotlinx.datetime.LocalTime
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.KeyValueDefinitionContext
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ContextualMapDefinitionTest {
    @Suppress("UNCHECKED_CAST")
    private val mapToTest = mapOf(
        LocalTime(1, 55, 33) to "hello",
        LocalTime(14, 22, 23) to "goodBye"
    ) as Map<Any, Any>

    private val def = ContextualMapDefinition<Any, Any, KeyValueDefinitionContext>(
        contextualResolver = { context.mapDefinition }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = KeyValueDefinitionContext(
        definitionsContext = null,
        keyDefinition = TestMarykModel.map.keyDefinition as IsSimpleValueDefinition<Any, IsPropertyContext>,
        valueDefinition = TestMarykModel.map.valueDefinition as IsValueDefinition<Any, IsPropertyContext>
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
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(8u) { key.tag }
        }

        fun readValue(map: MutableMap<Any, Any>) {
            val length = ProtoBuf.getLength(LENGTH_DELIMITED, bc::read)
            def.readTransportBytes(length, bc::read, this.context, map)
        }

        val mutableMap = mutableMapOf<Any, Any>()

        value.forEach {
            readKey()
            readValue(mutableMap)
            expect(it.value) { mutableMap[it.key] }
        }
    }

    @Test
    fun convertJson() {
        var totalString = ""
        def.writeJsonValue(mapToTest, JsonWriter { totalString += it }, this.context)

        expect("""{"01:55:33":"hello","14:22:23":"goodBye"}""") { totalString }

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader, this.context)

        expect(this.mapToTest) { converted }
    }
}
