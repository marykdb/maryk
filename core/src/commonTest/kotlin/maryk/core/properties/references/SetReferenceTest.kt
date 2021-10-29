package maryk.core.properties.references

import kotlinx.datetime.LocalDate
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.expect

class SetReferenceTest {
    private val setReference = TestMarykModel { embeddedValues { marykModel { set::ref } } }
    private val reference = TestMarykModel { set refAt LocalDate(2001, 4, 2) }
    private val subReference = TestMarykModel { embeddedValues { marykModel { set refAt LocalDate(2001, 4, 2) } } }

    @Test
    fun cacheReferenceTest() {
        assertSame(setReference, TestMarykModel { embeddedValues { marykModel { set::ref } } })
    }

    @Test
    fun getValueFromSet() {
        val list = setOf(
            LocalDate(2001, 4, 2),
            LocalDate(2005, 5, 22),
            LocalDate(2013, 10, 1)
        )

        expect(LocalDate(2001, 4, 2)) { this.reference.resolveFromAny(list) }

        assertFailsWith<UnexpectedValueException> {
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
        assertEquals(this.reference, converted)
    }

    @Test
    fun convertToStringAndBack() {
        expect("set.#2001-04-02") { this.reference.completeName }

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        expect(this.reference) { converted }
    }

    @Test
    fun writeSetRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            setReference.calculateStorageByteLength()
        )
        setReference.writeStorageBytes(bc::write)

        expect("661e4b") { bc.bytes!!.toHex() }
    }

    @Test
    fun writeStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            reference.calculateStorageByteLength()
        )
        reference.writeStorageBytes(bc::write)

        expect("4b0480002c96") { bc.bytes!!.toHex() }

        expect(reference) { TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun writeDeepStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subReference.calculateStorageByteLength()
        )
        subReference.writeStorageBytes(bc::write)

        expect("661e4b0480002c96") { bc.bytes!!.toHex() }

        expect(subReference) { TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }
}
