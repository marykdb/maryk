package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import maryk.test.shouldNotBe
import kotlin.test.Test

private object Properties : PropertyDefinitions<Any>()

private val modelDefinition = Properties.add(2, "subModel", SubModelDefinition(
    dataModel = { TestMarykObject }
))

private val definition = Properties.add(1, "test", StringDefinition())

private val ref = definition.getRef()
private val subRef = definition.getRef(modelDefinition.getRef())

internal class PropertyReferenceTest {
    @Test
    fun getCompleteName() {
        ref.completeName shouldBe "test"
        subRef.completeName shouldBe "subModel.test"
    }

    @Test
    fun testHashCode() {
        ref.hashCode() shouldBe 3556498
    }

    @Test
    fun testCompareTo() {
        ref shouldBe  definition.getRef()
        ref shouldNotBe modelDefinition.getRef()
    }

    @Test
    fun testProtoBufWrite() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            subRef.calculateTransportByteLength(cache)
        )
        subRef.writeTransportBytes(cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0201"
    }
}
