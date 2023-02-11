package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.SimpleMarykTypeEnum.S2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.expect

class TypedValueReferenceTest {
    private val typedValueReference =
        TestMarykModel { multi refAtType S2 }

    @Test
    fun cacheReferenceTest() {
        assertSame(typedValueReference, TestMarykModel { multi refAtType S2 })
    }

    @Test
    fun getValueFromMap() {
        val typedValue = TypedValue(
            T1,
            "string"
        )

        expect("string") { this.typedValueReference.resolveFromAny(typedValue) }

        assertFailsWith<UnexpectedValueException> {
            this.typedValueReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun testCompleteName() {
        expect("multi.*S2") { typedValueReference.completeName }
    }

    @Test
    fun writeAndReadStringValue() {
        expect(typedValueReference) { TestMarykModel.getPropertyReferenceByName(typedValueReference.completeName) }
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            typedValueReference.calculateTransportByteLength(cache)
        )
        typedValueReference.writeTransportBytes(cache, bc::write)

        expect("0d0002") { bc.bytes!!.toHex() }

        expect(typedValueReference) { TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read) }
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            typedValueReference.calculateStorageByteLength()
        )
        typedValueReference.writeStorageBytes(bc::write)

        expect("6915") { bc.bytes!!.toHex() }

        expect(typedValueReference) { TestMarykModel.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }
}
