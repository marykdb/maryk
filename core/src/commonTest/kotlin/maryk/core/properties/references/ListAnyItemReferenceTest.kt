package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class ListAnyItemReferenceTest {
    private val anyReference = TestMarykModel { listOfString.refToAny() }
    val cache = WriteCache()

    @Test
    fun resolveValues() {
        anyReference.resolve(listOf("a", "b")) shouldBe listOf("a", "b")
    }

    @Test
    fun convertAnyToProtoBufAndBack() {
        ByteCollector().apply {
            reserve(
                anyReference.calculateTransportByteLength(cache)
            )
            anyReference.writeTransportBytes(cache, ::write)

            TestMarykModel.getPropertyReferenceByBytes(size, ::read) shouldBe anyReference
        }
    }

    @Test
    fun convertAnyToStringAndBack() {
        this.anyReference.completeName shouldBe "listOfString.*"

        TestMarykModel.getPropertyReferenceByName(this.anyReference.completeName) shouldBe this.anyReference
    }

    @Test
    fun createAnyRefQualifierMatcher() {
        val matcher = anyReference.toQualifierMatcher()

        shouldBeOfType<QualifierFuzzyMatcher>(matcher).let {
            it.firstPossible().toHex() shouldBe "7a"
            it.qualifierParts.size shouldBe 1
            it.fuzzyMatchers.size shouldBe 1

            it.fuzzyMatchers.first().let { matcher ->
                (matcher is FuzzyExactLengthMatch) shouldBe true
                (matcher as FuzzyExactLengthMatch).length shouldBe 4
            }
        }
    }
}
