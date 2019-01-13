package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldNotBe
import maryk.test.shouldThrow
import kotlin.test.Test

private object Properties : PropertyDefinitions() {
    val definition = add(1, "test", StringDefinition())
    val modelDefinition = add(2, "embeddedObject", EmbeddedValuesDefinition(
        dataModel = { Model }
    ))
}

private object Model : RootDataModel<Model, Properties>(
    "name", properties = Properties
)

private val ref = Properties.definition.getRef()
private val subRef = Properties.definition.getRef(Properties.modelDefinition.getRef())

internal class PropertyReferenceTest {
    @Test
    fun getValueFromList() {
        val values = Model.values {
            mapNonNulls (
                definition with "±testValue"
            )
        }

        ref.resolveFromAny(values) shouldBe "±testValue"

        shouldThrow<UnexpectedValueException> {
            ref.resolveFromAny(123)
        }
    }

    @Test
    fun unwrap() {
        val list = subRef.unwrap()

        list[0].completeName shouldBe "embeddedObject"
        list[1] shouldBe subRef
    }

    @Test
    fun getCompleteName() {
        ref.completeName shouldBe "test"
        subRef.completeName shouldBe "embeddedObject.test"
    }

    @Test
    fun testHashCode() {
        ref.hashCode() shouldBe "test".hashCode()
    }

    @Test
    fun testCompareTo() {
        ref shouldBe Properties.definition.getRef()
        ref shouldNotBe Properties.modelDefinition.getRef()
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            subRef.calculateTransportByteLength(cache)
        )
        subRef.writeTransportBytes(cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0201"

        Properties.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe subRef
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subRef.calculateStorageByteLength()
        )
        subRef.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "1609"

        Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe subRef
    }
}
