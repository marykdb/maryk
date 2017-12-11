package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe
import maryk.test.shouldNotBe
import kotlin.test.Test

internal class PropertyReferenceTest {

    private object Properties : PropertyDefinitions<Any>()

    private val modelDefinition = Properties.add(2, "subModel", SubModelDefinition(
            dataModel = { TestMarykObject }
    ))

    private val definition = Properties.add(1, "test", StringDefinition())

    private val ref = this.definition.getRef()
    private val subRef = this.definition.getRef(modelDefinition.getRef())

    @Test
    fun getCompleteName() {
        this.ref.completeName shouldBe "test"
        this.subRef.completeName shouldBe "subModel.test"
    }

    @Test
    fun testHashCode() {
        this.ref.hashCode() shouldBe 3556498
    }

    @Test
    fun testCompareTo() {
        this.ref shouldBe  definition.getRef()
        this.ref shouldNotBe modelDefinition.getRef()
    }

    @Test
    fun testProtoBufWrite() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            this.subRef.calculateTransportByteLength(cache)
        )
        this.subRef.writeTransportBytes(cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0201"
    }
}