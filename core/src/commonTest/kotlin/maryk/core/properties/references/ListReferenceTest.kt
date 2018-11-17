package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class ListReferenceTest {
    private val listReference = TestMarykModel { embeddedValues { marykModel ref { listOfString } } }
    private val reference = TestMarykModel { listOfString refAt 5 }
    private val subReference = TestMarykModel { embeddedValues { marykModel { listOfString refAt 22 } } }
    val cache = WriteCache()

    @Test
    fun getValueFromList() {
        val list = listOf('a','b','c','d','e','f', 'g')

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
    fun convertToStringAndBack() {
        this.reference.completeName shouldBe "listOfString.@5"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }

    @Test
    fun writeListRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            listReference.calculateStorageByteLength()
        )
        listReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "61197a"
    }

    @Test
    fun writeStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            reference.calculateStorageByteLength()
        )
        reference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "7a05"
    }

    @Test
    fun writeDeepStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subReference.calculateStorageByteLength()
        )
        subReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "61197a16"
    }
}
