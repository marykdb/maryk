package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.Test

class ListItemReferenceTest {
    private val reference = TestMarykModel { listOfString refAt 5u }
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
        ByteCollector().apply {
            reserve(
                reference.calculateTransportByteLength(cache)
            )
            reference.writeTransportBytes(cache, ::write)

            TestMarykModel.getPropertyReferenceByBytes(size, ::read) shouldBe reference
        }
    }

    @Test
    fun convertToStringAndBack() {
        this.reference.completeName shouldBe "listOfString.@5"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }

    @Test
    fun writeAndReadStorageBytes() {
        ByteCollector().apply {
            reserve(
                reference.calculateStorageByteLength()
            )
            reference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "7a00000005"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe reference
        }
    }

    @Test
    fun writeDeepStorageBytes() {
        ByteCollector().apply {
            reserve(
                subReference.calculateStorageByteLength()
            )
            subReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "661e7a00000016"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe subReference
        }
    }

    @Test
    fun createItemRefQualifierMatcher() {
        val matcher = reference.toQualifierMatcher()

        shouldBeOfType<QualifierExactMatcher>(matcher).qualifier.toHex()  shouldBe "7a00000005"
    }
}
