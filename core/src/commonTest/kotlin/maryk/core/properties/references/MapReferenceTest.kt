package maryk.core.properties.references

import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class MapReferenceTest {
    private val mapReference = TestMarykModel { map::ref }

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            val cache = WriteCache()

            reserve(
                mapReference.calculateTransportByteLength(cache)
            )
            mapReference.writeTransportBytes(cache, ::write)

            TestMarykModel.getPropertyReferenceByBytes(size, ::read) shouldBe mapReference
        }
    }

    @Test
    fun testStringConversion() {
        mapReference.completeName shouldBe "map"

        TestMarykModel.getPropertyReferenceByName(mapReference.completeName) shouldBe mapReference
    }

    @Test
    fun writeAndReadMapRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                mapReference.calculateStorageByteLength()
            )
            mapReference.writeStorageBytes(::write)

            bytes!!.toHex() shouldBe "54"

            TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) shouldBe mapReference
        }
    }
}
