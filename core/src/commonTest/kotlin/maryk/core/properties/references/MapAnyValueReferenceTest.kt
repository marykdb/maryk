package maryk.core.properties.references

import kotlinx.datetime.LocalTime
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.expect

class MapAnyValueReferenceTest {
    private val anyReference = TestMarykModel { map.refToAny() }
    private val subAnyReference = TestMarykModel { embeddedValues { marykModel { map.refToAny() } } }

    @Test
    fun cacheReferenceTest() {
        assertSame(anyReference, TestMarykModel { map.refToAny() })
        assertSame(subAnyReference, TestMarykModel { embeddedValues { marykModel { map.refToAny() } } })
    }

    @Test
    fun resolveValues() {
        expect(listOf("c", "d")) {
            anyReference.resolve(
                mapOf(
                    LocalTime(0, 0, 0) to "c",
                    LocalTime(12, 12, 12) to "d"
                )
            )
        }
    }

    @Test
    fun testStringConversion() {
        expect("map.*") { anyReference.completeName }

        expect(anyReference) { TestMarykModel.getPropertyReferenceByName(anyReference.completeName) }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        anyReference
        bc.reserve(
            anyReference.calculateTransportByteLength(cache)
        )
        anyReference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        assertEquals(anyReference, converted)
        bc.reset()
    }

    @Test
    fun createAnyRefQualifierMatcher() {
        val matcher = anyReference.toQualifierMatcher()

        assertIs<QualifierFuzzyMatcher>(matcher).let {
            expect("54") { it.firstPossible().toHex() }
            expect(1) { it.qualifierParts.size }
            expect(1) { it.fuzzyMatchers.size }

            it.fuzzyMatchers.first().let { matcher ->
                assertIs<FuzzyExactLengthMatch>(matcher).apply {
                    expect(3) { length }
                }
            }
        }
    }
}
