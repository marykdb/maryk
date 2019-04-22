package maryk.core.properties.references

import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
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
        val bc = ByteCollector()

        bc.reserve(
            this.anyReference.calculateTransportByteLength(cache)
        )
        this.anyReference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.anyReference
    }

    @Test
    fun convertAnyToStringAndBack() {
        this.anyReference.completeName shouldBe "listOfString.*"

        val converted = TestMarykModel.getPropertyReferenceByName(this.anyReference.completeName)
        converted shouldBe this.anyReference
    }

    @Test
    fun writeAndReadAnyStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            anyReference.calculateStorageByteLength()
        )
        anyReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "180f00"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe anyReference
    }

    @Test
    fun createAnyRefQualifierMatcher() {
        val matcher = anyReference.toQualifierMatcher()

        (matcher is QualifierFuzzyMatcher) shouldBe true
        (matcher as QualifierFuzzyMatcher).let {
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
