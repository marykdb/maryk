package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class SetReferenceTest {
    private val setReference = TestMarykModel { embeddedValues { marykModel ref { set } } }
    private val reference = TestMarykModel { set refAt Date(2001, 4, 2) }
    private val subReference = TestMarykModel { embeddedValues { marykModel { set refAt Date(2001, 4, 2) } }}

    @Test
    fun getValueFromSet() {
        val list = setOf(
            Date(2001, 4, 2),
            Date(2005, 5, 22),
            Date(2013, 10, 1)
        )

        this.reference.resolveFromAny(list) shouldBe Date(2001, 4, 2)

        shouldThrow<UnexpectedValueException> {
            this.reference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
    }

    @Test
    fun convertToStringAndBack() {
        this.reference.completeName shouldBe "set.$2001-04-02"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }

    @Test
    fun writeSetRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            setReference.calculateStorageByteLength()
        )
        setReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "61194b"
    }

    @Test
    fun writeStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            reference.calculateStorageByteLength()
        )
        reference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "4b80002c96"
    }

    @Test
    fun writeDeepStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subReference.calculateStorageByteLength()
        )
        subReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "61194b80002c96"
    }
}
