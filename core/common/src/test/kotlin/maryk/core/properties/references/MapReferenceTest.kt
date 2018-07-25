package maryk.core.properties.references

import maryk.TestMarykModel
import maryk.core.protobuf.WriteCache
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class MapReferenceTest {
    private val mapReference = TestMarykModel.ref { map }
    private val keyReference = TestMarykModel { map key Time(12, 0, 1) }
    private val valReference = TestMarykModel { map at Time(15, 22, 55) }

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
