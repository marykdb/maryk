package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.assertType
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ListReferenceTest {
    private val listReference = TestMarykModel { embeddedValues { marykModel { listOfString::ref } } }
    val cache = WriteCache()

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            reserve(
                listReference.calculateTransportByteLength(cache)
            )
            listReference.writeTransportBytes(cache, ::write)

            expect(listReference) { TestMarykModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun convertToStringAndBack() {
        expect("embeddedValues.marykModel.listOfString") { this.listReference.completeName }
        expect(this.listReference) { TestMarykModel.getPropertyReferenceByName(this.listReference.completeName) }
    }

    @Test
    fun writeListRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                listReference.calculateStorageByteLength()
            )
            listReference.writeStorageBytes(::write)

            expect("661e7a") { bytes!!.toHex() }
        }
    }

    @Test
    fun writeAndReadStorageBytes() {
        ByteCollector().apply {
            reserve(
                listReference.calculateStorageByteLength()
            )
            listReference.writeStorageBytes(::write)

            expect("661e7a") { bytes!!.toHex() }

            expect(listReference) { TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) }
        }
    }

    @Test
    fun createItemRefQualifierMatcher() {
        val matcher = listReference.toQualifierMatcher()

        expect("661e7a") {
            assertType<QualifierExactMatcher>(matcher).qualifier.toHex()
        }
    }
}
