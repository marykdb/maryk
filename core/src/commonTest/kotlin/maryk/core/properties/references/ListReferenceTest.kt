package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class ListReferenceTest {
    private val listReference = TestMarykModel { embeddedValues { marykModel ref { listOfString } } }
    private val reference = TestMarykModel { listOfString refAt 5u }
    private val anyReference = TestMarykModel { listOfString.refToAny() }
    private val subReference = TestMarykModel { embeddedValues { marykModel { listOfString refAt 22u } } }
    val cache = WriteCache()

    @Test
    fun getValueFromList() {
        val list = listOf('a', 'b', 'c', 'd', 'e', 'f', 'g')

        this.reference.resolveFromAny(list) shouldBe 'f'

        shouldThrow<UnexpectedValueException> {
            this.reference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()

        bc.reserve(
            this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
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
    fun convertToStringAndBack() {
        this.reference.completeName shouldBe "listOfString.@5"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }

    @Test
    fun convertAnyToStringAndBack() {
        this.anyReference.completeName shouldBe "listOfString.*"

        val converted = TestMarykModel.getPropertyReferenceByName(this.anyReference.completeName)
        converted shouldBe this.anyReference
    }

    @Test
    fun writeListRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            listReference.calculateStorageByteLength()
        )
        listReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "661e7a"
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            reference.calculateStorageByteLength()
        )
        reference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "7a00000005"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe reference
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

    @Test
    fun writeDeepStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subReference.calculateStorageByteLength()
        )
        subReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "661e7a00000016"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe subReference
    }

    @Test
    fun createItemRefQualifierMatcher() {
        val matcher = reference.toQualifierMatcher()

        (matcher is QualifierExactMatcher) shouldBe true
        (matcher as QualifierExactMatcher).qualifier.toHex() shouldBe "7a00000005"
    }
}
