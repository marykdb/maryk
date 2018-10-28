package maryk.core.properties.definitions.contextual

import maryk.TestMarykModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinitionContext
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
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
    private val def = ContextualCollectionDefinition<ListDefinitionContext>(
        contextualResolver = { it!!.listDefinition as IsByteTransportableCollection<Any, Collection<Any>, ListDefinitionContext> }
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
