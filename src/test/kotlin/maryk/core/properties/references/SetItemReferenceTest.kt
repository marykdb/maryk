package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.types.Date
import org.junit.Test
import kotlin.test.assertEquals

class SetItemReferenceTest {
    val reference = TestMarykObject.Properties.set.getItemRef(Date(2001, 4, 2))

    @Test
    fun testProtoBufConversion() {
        val bc = ByteCollectorWithLengthCacher()

        bc.reserve(
                this.reference.calculateTransportByteLength(bc::addToCache)
        )
        this.reference.writeTransportBytes(bc::nextLengthFromCache, bc::write)

        val converted = TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read)
        assertEquals(this.reference, converted)
    }
}