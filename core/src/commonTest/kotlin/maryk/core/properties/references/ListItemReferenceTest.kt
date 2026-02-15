package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.expect

class ListItemReferenceTest {
    private val reference = TestMarykModel { listOfString refAt 5u }
    private val subReference = TestMarykModel { embeddedValues { marykModel { listOfString refAt 22u } } }
    val cache = WriteCache()

    @Test
    fun cacheReferenceTest() {
        assertSame(reference, TestMarykModel { listOfString refAt 5u })
        assertSame(subReference, TestMarykModel { embeddedValues { marykModel { listOfString refAt 22u } } })
    }

    @Test
    fun getValueFromList() {
        val list = listOf('a', 'b', 'c', 'd', 'e', 'f', 'g')

        expect('f') { this.reference.resolveFromAny(list) }

        assertFailsWith<UnexpectedValueException> {
            this.reference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            reserve(
                reference.calculateTransportByteLength(cache)
            )
            reference.writeTransportBytes(cache, ::write)

            expect(reference) { TestMarykModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun convertToStringAndBack() {
        expect("listOfString.@5") { this.reference.completeName }

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        expect(this.reference) { converted }
    }

    @Test
    fun writeAndReadStorageBytes() {
        ByteCollector().apply {
            reserve(
                reference.calculateStorageByteLength()
            )
            reference.writeStorageBytes(::write)

            expect("7a00000005") { bytes!!.toHexString() }

            expect(reference) { TestMarykModel.getPropertyReferenceByStorageBytes(size, ::read) }
        }
    }

    @Test
    fun writeDeepStorageBytes() {
        ByteCollector().apply {
            reserve(
                subReference.calculateStorageByteLength()
            )
            subReference.writeStorageBytes(::write)

            expect("661e7a00000016") { bytes!!.toHexString() }

            expect(subReference) { TestMarykModel.getPropertyReferenceByStorageBytes(size, ::read) }
        }
    }

    @Test
    fun createItemRefQualifierMatcher() {
        val matcher = reference.toQualifierMatcher()

        expect("7a00000005") {
            assertIs<QualifierExactMatcher>(matcher).qualifier.toHexString()
        }
    }
}
