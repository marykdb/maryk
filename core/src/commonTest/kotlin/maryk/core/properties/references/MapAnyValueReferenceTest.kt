package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class MapAnyValueReferenceTest {
    private val anyReference = TestMarykModel { map.refToAny() }
    private val subAnyReference = TestMarykModel { embeddedValues { marykModel { map.refToAny() } } }

    @Test
    fun resolveValues() {
        anyReference.resolve(
            mapOf(
                Time(0,0,0) to "c",
                Time(12, 12, 12) to "d"
            )
        ) shouldBe listOf("c", "d")
    }

    @Test
    fun testStringConversion() {
        anyReference.completeName shouldBe "map.*"

        TestMarykModel.getPropertyReferenceByName(anyReference.completeName) shouldBe anyReference
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
        converted shouldBe anyReference
        bc.reset()
    }

    @Test
    fun writeAndReadAnyRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            anyReference.calculateStorageByteLength()
        )
        anyReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "100a00"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe anyReference
    }

    @Test
    fun createAnyRefQualifierMatcher() {
        val matcher = anyReference.toQualifierMatcher()

        shouldBeOfType<QualifierFuzzyMatcher>(matcher).let {
            it.firstPossible().toHex() shouldBe "54"
            it.qualifierParts.size shouldBe 1
            it.fuzzyMatchers.size shouldBe 1

            it.fuzzyMatchers.first().let { matcher ->
                (matcher is FuzzyExactLengthMatch) shouldBe true
                (matcher as FuzzyExactLengthMatch).length shouldBe 3
            }
        }
    }

    @Test
    fun writeAndReadDeepAnyRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                subAnyReference.calculateStorageByteLength()
            )
            subAnyReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "661e100a00"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe subAnyReference
        }
    }
}
