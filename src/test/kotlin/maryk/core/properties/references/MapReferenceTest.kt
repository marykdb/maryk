package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.Time
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe
import kotlin.test.Test

class MapReferenceTest {
    val mapReference = TestMarykObject.ref { map }
    val keyReference = TestMarykObject { map key Time(12, 0, 1) }
    val valReference = TestMarykObject { map at Time(15, 22, 55) }

    @Test
    fun testProtoBufConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        arrayOf(mapReference, keyReference, valReference).forEach {
            bc.reserve(
                    it.calculateTransportByteLength(cache)
            )
            it.writeTransportBytes(cache, bc::write)

            val converted = TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read)
            converted shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStringConversion() {
        mapReference.completeName shouldBe "map"
        keyReference.completeName shouldBe "map.\$12:00:01"
        valReference.completeName shouldBe "map.@15:22:55"

        arrayOf(mapReference, keyReference, valReference).forEach {
            val converted = TestMarykObject.getPropertyReferenceByName(it.completeName!!)
            converted shouldBe it
        }
    }
}