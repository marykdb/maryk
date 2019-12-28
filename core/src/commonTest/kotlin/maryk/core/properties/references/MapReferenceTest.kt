package maryk.core.properties.references

import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.expect

class MapReferenceTest {
    private val mapReference = TestMarykModel { map::ref }

    @Test
    fun cacheReferenceTest() {
        assertSame(mapReference, TestMarykModel { map::ref })
    }

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            val cache = WriteCache()

            reserve(
                mapReference.calculateTransportByteLength(cache)
            )
            mapReference.writeTransportBytes(cache, ::write)

            expect(mapReference) { TestMarykModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun testStringConversion() {
        expect("map") { mapReference.completeName }
        expect(mapReference) { TestMarykModel.getPropertyReferenceByName(mapReference.completeName) }
    }

    @Test
    fun writeAndReadMapRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                mapReference.calculateStorageByteLength()
            )
            mapReference.writeStorageBytes(::write)

            expect("54") { bytes!!.toHex() }

            expect(mapReference) { TestMarykModel.Properties.getPropertyReferenceByStorageBytes(size, ::read) }
        }
    }
}
