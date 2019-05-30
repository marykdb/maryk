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
import kotlin.test.expect

class TypedValueReferenceTest {
    private val typeReference =
        TestMarykModel { multi refAtType S2 }

    @Test
    fun getValueFromMap() {
        val typedValue = TypedValue(
            T1,
            "string"
        )

        expect("string") { this.typeReference.resolveFromAny(typedValue) }

        assertFailsWith<UnexpectedValueException> {
            this.typeReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun testCompleteName() {
        expect("multi.*S2") { typeReference.completeName }
    }

    @Test
    fun writeAndReadStringValue() {
        expect(typeReference) { TestMarykModel.Properties.getPropertyReferenceByName(typeReference.completeName) }
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            typeReference.calculateTransportByteLength(cache)
        )
        typeReference.writeTransportBytes(cache, bc::write)

        expect("0d0002") { bc.bytes!!.toHex() }

        expect(typeReference) { TestMarykModel.Properties.getPropertyReferenceByBytes(bc.size, bc::read) }
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            typeReference.calculateStorageByteLength()
        )
        typeReference.writeStorageBytes(bc::write)

        expect("6915") { bc.bytes!!.toHex() }

        expect(typeReference) { TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }
}
