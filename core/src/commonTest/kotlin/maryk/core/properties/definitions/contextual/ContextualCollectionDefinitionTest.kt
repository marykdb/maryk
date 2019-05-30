package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinitionContext
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ContextualCollectionDefinitionTest {
    private val listToTest = listOf(
        "T1", "T2", "T3"
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualCollectionDefinition<ListDefinitionContext>(
        contextualResolver = { it!!.listDefinition as IsSerializablePropertyDefinition<Collection<Any>, ListDefinitionContext> }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = ListDefinitionContext(
        definitionsContext = null,
        valueDefinition = TestMarykModel.properties.listOfString.valueDefinition as IsValueDefinition<Any, IsPropertyContext>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val values = listOf("T", "T2", "T3", "T4")
        val asHex = "ea020154ea02025432ea02025433ea02025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(45u, values, cache, this.context)
        )
        def.writeTransportBytesWithKey(45u, values, cache, bc::write, this.context)

        expect(asHex) { bc.bytes!!.toHex() }

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(45u) { key.tag }
        }

        fun readValue(list: List<String>) = def.readTransportBytes(
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
            bc::read,
            this.context,
            list
        )

        val mutableList = mutableListOf<String>()

        values.forEach { value ->
            readKey()
            readValue(mutableList)

            expect(value) { mutableList.last() }
        }
    }

    @Test
    fun convertJson() {
        var totalString = ""
        def.writeJsonValue(listToTest, JsonWriter { totalString += it }, this.context)

        expect("""["T1","T2","T3"]""") { totalString }

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader, this.context)

        expect(listToTest) { converted }
    }
}
