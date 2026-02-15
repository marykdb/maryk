package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.expect

class ListAnyItemReferenceTest {
    private val anyReference = TestMarykModel { listOfString.refToAny() }
    val cache = WriteCache()

    @Test
    fun cacheReferenceTest() {
        assertSame(anyReference, TestMarykModel { listOfString.refToAny() })
    }

    @Test
    fun resolveValues() {
        expect(listOf("a", "b")) { anyReference.resolve(listOf("a", "b")) }
    }

    @Test
    fun convertAnyToProtoBufAndBack() {
        ByteCollector().apply {
            reserve(
                anyReference.calculateTransportByteLength(cache)
            )
            anyReference.writeTransportBytes(cache, ::write)

            expect(anyReference) { TestMarykModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun convertAnyToStringAndBack() {
        expect("listOfString.*") { this.anyReference.completeName }

        expect(this.anyReference) { TestMarykModel.getPropertyReferenceByName(this.anyReference.completeName) }
    }

    @Test
    fun createAnyRefQualifierMatcher() {
        val matcher = anyReference.toQualifierMatcher()

        assertIs<QualifierFuzzyMatcher>(matcher).let {
            expect("7a") { it.firstPossible().toHexString() }
            expect(1) { it.qualifierParts.size }
            expect(1) { it.fuzzyMatchers.size }

            it.fuzzyMatchers.first().let { matcher ->
                assertIs<FuzzyExactLengthMatch>(matcher).apply {
                    expect(4) { length }
                }
            }
        }
    }
}
