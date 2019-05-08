package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class MapKeyReferenceTest {
    private val keyReference = TestMarykModel { map refToKey Time(12, 0, 1) }
    private val subKeyReference = TestMarykModel { embeddedValues { marykModel { map refToKey Time(15, 22, 55) } } }

    @Test
    fun getValueFromMap() {
        val map = mapOf(
            Time(12, 0, 1) to "right",
            Time(15, 22, 55) to "right2",
            Time(0, 0, 1) to "wrong",
            Time(2, 14, 52) to "wrong again"
        )

        this.keyReference.resolveFromAny(map) shouldBe Time(12, 0, 1)

        shouldThrow<UnexpectedValueException> {
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
        converted shouldBe keyReference
        bc.reset()
    }

    @Test
    fun testStringConversion() {
        keyReference.completeName shouldBe "map.#12:00:01"
        TestMarykModel.getPropertyReferenceByName(keyReference.completeName) shouldBe keyReference
    }

    @Test
    fun testStringConversionForSub() {
        subKeyReference.completeName shouldBe "embeddedValues.marykModel.map.#15:22:55"
        TestMarykModel.getPropertyReferenceByName(subKeyReference.completeName) shouldBe subKeyReference
    }
}
