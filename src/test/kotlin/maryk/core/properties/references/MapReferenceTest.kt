package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.types.Time
import maryk.test.shouldBe
import kotlin.test.Test

class MapReferenceTest {
    val mapReference = TestMarykObject.Properties.map.getRef()
    val keyReference = TestMarykObject.Properties.map.getKeyRef(Time(12, 0, 1))
    val valReference = TestMarykObject.Properties.map.getValueRef(Time(15, 22, 55))

    @Test
    fun testProtoBufConversion() {
        val bc = ByteCollectorWithLengthCacher()
        arrayOf(mapReference, keyReference, valReference).forEach {
            bc.reserve(
                    it.calculateTransportByteLength(bc::addToCache)
            )
            it.writeTransportBytes(bc::nextLengthFromCache, bc::write)

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