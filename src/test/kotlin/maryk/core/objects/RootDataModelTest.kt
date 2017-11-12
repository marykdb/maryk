package maryk.core.objects

import maryk.Option
import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test
import kotlin.test.assertEquals

internal class RootDataModelTest {
    @Test
    fun testKey() {
        assertEquals(
                Bytes(
                        byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
                ),
                TestMarykObject.key.getKey(
                        TestMarykObject(
                                string = "name",
                                int = 5123123,
                                uint = 555.toUInt32(),
                                double = 6.33,
                                bool = true,
                                enum = Option.V2,
                                dateTime = DateTime.nowUTC()
                        )
                )
        )
    }

    private val subModelRef = SubMarykObject.Properties.value.getRef { TestMarykObject.Properties.subModel.getRef() }
    private val mapRef = TestMarykObject.Properties.map.getRef()

    @Test
    fun testPropertyReferenceByName() {
        assertEquals(mapRef, TestMarykObject.getPropertyReferenceByName(mapRef.completeName!!))
        assertEquals(subModelRef, TestMarykObject.getPropertyReferenceByName(subModelRef.completeName!!))
    }

    @Test
    fun testPropertyReferenceByWriter() {
        val bc = ByteCollectorWithLengthCacher()

        arrayOf(subModelRef, mapRef).forEach {
            bc.reserve(
                    it.calculateTransportByteLength(bc::addToCache)
            )
            it.writeTransportBytes(bc::nextLengthFromCache, bc::write)

            assertEquals(it, TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read))

            bc.reset()
        }
    }
}