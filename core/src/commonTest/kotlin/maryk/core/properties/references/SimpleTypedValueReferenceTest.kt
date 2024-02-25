package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.types.invoke
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.Measurement
import maryk.test.models.MeasurementType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.expect

class SimpleTypedValueReferenceTest {
    private val typedValueReference =
        Measurement { measurement simpleRefAtType MeasurementType.Number }

    @Test
    fun cacheReferenceTest() {
        assertSame(typedValueReference, Measurement { measurement simpleRefAtType MeasurementType.Number })
    }

    @Test
    fun getValueFromMap() {
        val typedValueWrong = T1("string")

        assertFailsWith<UnexpectedValueException> {
            this.typedValueReference.resolveFromAny(typedValueWrong)
        }

        val typedValue = MeasurementType.Number(15.toShort())

        expect(15.toShort()) { this.typedValueReference.resolveFromAny(typedValue) }

        assertFailsWith<UnexpectedValueException> {
            this.typedValueReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun testCompleteName() {
        expect("measurement.>Number") { typedValueReference.completeName }
    }

    @Test
    fun writeAndReadStringValue() {
        expect(typedValueReference) { Measurement.getPropertyReferenceByName(typedValueReference.completeName) }
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            typedValueReference.calculateTransportByteLength(cache)
        )
        typedValueReference.writeTransportBytes(cache, bc::write)

        expect("020803") { bc.bytes!!.toHex() }

        expect(typedValueReference) { Measurement.getPropertyReferenceByBytes(bc.size, bc::read) }
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            typedValueReference.calculateStorageByteLength()
        )
        typedValueReference.writeStorageBytes(bc::write)

        expect("11") { bc.bytes!!.toHex() }
    }
}
