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
import maryk.test.shouldBe
import kotlin.test.Test

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
        valueDefinion = TestMarykModel.properties.listOfString.valueDefinition as IsValueDefinition<Any, IsPropertyContext>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "ea020154ea02025432ea02025433ea02025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(45u, value, cache, this.context)
        )
        def.writeTransportBytesWithKey(45u, value, cache, bc::write, this.context)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe LENGTH_DELIMITED
            key.tag shouldBe 45u
        }

        fun readValue(list: List<String>) = def.readTransportBytes(
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
            bc::read,
            this.context,
            list
        )

        val mutableList = mutableListOf<String>()

        value.forEach {
            readKey()
            readValue(mutableList)

            mutableList.last() shouldBe it
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
