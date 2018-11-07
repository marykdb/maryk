package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class MapReferenceTest {
    private val mapReference = TestMarykModel.ref { map }
    private val keyReference = TestMarykModel { map refToKey Time(12, 0, 1) }
    private val valReference = TestMarykModel { map refAt Time(15, 22, 55) }

    @Test
    fun get_value_from_map() {
        val map = mapOf(
            Time(12, 0, 1) to "right",
            Time(15, 22, 55) to "right2",
            Time(0, 0, 1) to "wrong",
            Time(2, 14, 52) to "wrong again"
        )

        this.keyReference.resolveFromAny(map) shouldBe Time(12, 0, 1)
        this.valReference.resolveFromAny(map) shouldBe "right2"

        shouldThrow<UnexpectedValueException> {
            this.keyReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        for (it in arrayOf(mapReference, keyReference, valReference)) {
            bc.reserve(
                it.calculateTransportByteLength(cache)
            )
            it.writeTransportBytes(cache, bc::write)

            val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
            converted shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        mapReference.completeName shouldBe "map"
        keyReference.completeName shouldBe "map.\$12:00:01"
        valReference.completeName shouldBe "map.@15:22:55"

        for (it in arrayOf(mapReference, keyReference, valReference)) {
            val converted = TestMarykModel.getPropertyReferenceByName(it.completeName)
            converted shouldBe it
        }
    }
}
