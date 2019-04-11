package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.MultiTypeEnum.T1
import maryk.test.models.MultiTypeEnum.T2
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class TypedValueReferenceTest {
    private val typeReference =
        TestMarykModel { multi refAtType T2 }

    @Test
    fun getValueFromMap() {
        val typedValue = TypedValue(
            T1,
            "string"
        )

        this.typeReference.resolveFromAny(typedValue) shouldBe "string"

        shouldThrow<UnexpectedValueException> {
            this.typeReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun testCompleteName() {
        typeReference.completeName shouldBe "multi.*T2"
    }

    @Test
    fun writeAndReadStringValue() {
        TestMarykModel.Properties.getPropertyReferenceByName(typeReference.completeName) shouldBe typeReference
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            typeReference.calculateTransportByteLength(cache)
        )
        typeReference.writeTransportBytes(cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0d0002"

        TestMarykModel.Properties.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe typeReference
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            typeReference.calculateStorageByteLength()
        )
        typeReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "6915"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe typeReference
    }
}
