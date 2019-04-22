package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class ListReferenceTest {
    private val listReference = TestMarykModel { embeddedValues { marykModel ref { listOfString } } }
    val cache = WriteCache()

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            reserve(
                listReference.calculateTransportByteLength(cache)
            )
            listReference.writeTransportBytes(cache, ::write)

            TestMarykModel.getPropertyReferenceByBytes(size, ::read) shouldBe listReference
        }
    }

    @Test
    fun convertToStringAndBack() {
        this.listReference.completeName shouldBe "embeddedValues.marykModel.listOfString"
        TestMarykModel.getPropertyReferenceByName(this.listReference.completeName) shouldBe this.listReference
    }

    @Test
    fun writeListRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                listReference.calculateStorageByteLength()
            )
            listReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "661e7a"
        }
    }

    @Test
    fun writeAndReadStorageBytes() {
        ByteCollector().apply {
            reserve(
                listReference.calculateStorageByteLength()
            )
            listReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "661e7a"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe listReference
        }
    }

    @Test
    fun createItemRefQualifierMatcher() {
        val matcher = listReference.toQualifierMatcher()

        shouldBeOfType<QualifierExactMatcher>(matcher).qualifier.toHex() shouldBe "7a1e66"
    }
}
