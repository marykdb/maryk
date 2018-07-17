package maryk.core.models

import maryk.EmbeddedMarykObject
import maryk.Option
import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCache
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.shouldBe
import kotlin.test.Test

internal class RootObjectDataModelTest {
    @Test
    fun testKey() {
        TestMarykObject.key(
            TestMarykObject(
                string = "name",
                int = 5123123,
                uint = 555.toUInt32(),
                double = 6.33,
                bool = true,
                enum = Option.V2,
                dateTime = DateTime.nowUTC()
            )
        ) shouldBe Bytes(
            byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
        )
    }

    private val subModelRef = EmbeddedMarykObject.Properties.value.getRef(TestMarykObject.Properties.embeddedObject.getRef())
    private val mapRef = TestMarykObject.ref { map }
    private val mapKeyRef = TestMarykObject { map key Time(12, 33, 44) }

    @Test
    fun testPropertyReferenceByName() {
        TestMarykObject.getPropertyReferenceByName(mapRef.completeName) shouldBe mapRef
        TestMarykObject.getPropertyReferenceByName(subModelRef.completeName) shouldBe subModelRef
    }

    @Test
    fun testPropertyReferenceByWriter() {
        val bc = ByteCollector()
        val cache = WriteCache()

        arrayOf(subModelRef, mapRef, mapKeyRef).forEach {
            bc.reserve(
                it.calculateTransportByteLength(cache)
            )
            it.writeTransportBytes(cache, bc::write)

            TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe it

            bc.reset()
        }
    }
}
