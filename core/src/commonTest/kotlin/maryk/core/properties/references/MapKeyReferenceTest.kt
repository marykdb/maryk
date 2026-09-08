package maryk.core.properties.references

import kotlinx.datetime.LocalTime
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.ComplexModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class MapKeyReferenceTest {
    private val keyReference = TestMarykModel.ref { map key LocalTime(12, 0, 1) }
    private val subKeyReference = TestMarykModel.ref { embeddedValues { marykModel { map key LocalTime(15, 22, 55) } } }

    @Test
    fun sameMapKeySelectionCreatesAnEqualReference() {
        assertEquals(keyReference, TestMarykModel.ref { map key LocalTime(12, 0, 1) })
        assertEquals(subKeyReference, TestMarykModel.ref { embeddedValues { marykModel { map key LocalTime(15, 22, 55) } } })
    }

    @Test
    fun getValueFromMap() {
        val map = mapOf(
            LocalTime(12, 0, 1) to "right",
            LocalTime(15, 22, 55) to "right2",
            LocalTime(0, 0, 1) to "wrong",
            LocalTime(2, 14, 52) to "wrong again"
        )

        expect(LocalTime(12, 0, 1)) { this.keyReference.resolveFromAny(map) }

        assertFailsWith<UnexpectedValueException> {
            this.keyReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            keyReference.calculateTransportByteLength(cache)
        )
        keyReference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        assertEquals(keyReference, converted)
        bc.reset()
    }

    @Test
    fun convertsLengthDelimitedMapKeyReferenceToProtoBufAndBack() {
        val stringKeyReference = ComplexModel.ref { mapStringString key "key" }

        ByteCollector().apply {
            val cache = WriteCache()

            reserve(
                stringKeyReference.calculateTransportByteLength(cache)
            )
            stringKeyReference.writeTransportBytes(cache, ::write)

            expect(stringKeyReference) { ComplexModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun testStringConversion() {
        expect("map.#12:00:01") { keyReference.completeName }
        expect(keyReference) { TestMarykModel.getPropertyReferenceByName(keyReference.completeName) }
    }

    @Test
    fun testStringConversionForSub() {
        expect("embeddedValues.marykModel.map.#15:22:55") { subKeyReference.completeName }
        expect(subKeyReference) { TestMarykModel.getPropertyReferenceByName(subKeyReference.completeName) }
    }
}
