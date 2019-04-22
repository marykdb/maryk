package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.Test

class MapValueReferenceTest {
    private val valReference = TestMarykModel { map refAt Time(15, 22, 55) }
    private val subReference = TestMarykModel { embeddedValues { marykModel { map refAt Time(15, 22, 55) } } }

    @Test
    fun getValueFromMap() {
        val map = mapOf(
            Time(12, 0, 1) to "right",
            Time(15, 22, 55) to "right2",
            Time(0, 0, 1) to "wrong",
            Time(2, 14, 52) to "wrong again"
        )

        this.valReference.resolveFromAny(map) shouldBe "right2"

        shouldThrow<UnexpectedValueException> {
            this.valReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            val cache = WriteCache()

            reserve(
                valReference.calculateTransportByteLength(cache)
            )
            valReference.writeTransportBytes(cache, ::write)

            TestMarykModel.getPropertyReferenceByBytes(size, ::read) shouldBe valReference
        }
    }

    @Test
    fun testStringConversion() {
        valReference.completeName shouldBe "map.@15:22:55"
        TestMarykModel.getPropertyReferenceByName(valReference.completeName) shouldBe valReference
    }

    @Test
    fun writeAndReadValueRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                valReference.calculateStorageByteLength()
            )
            valReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "540300d84f"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe valReference
        }

    }

    @Test
    fun writeAndReadDeepValueRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                subReference.calculateStorageByteLength()
            )
            subReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "661e540300d84f"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe subReference
        }
    }

    @Test
    fun createValueRefQualifierMatcher() {
        val matcher = subReference.toQualifierMatcher()

        shouldBeOfType<QualifierExactMatcher>(matcher).qualifier.toHex() shouldBe "540300d84f1e66"
    }
}
