package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollectorWithLengthCacher
import kotlin.test.assertEquals
import kotlin.test.Test

class ListItemReferenceTest {
    val reference = TestMarykObject.Properties.listOfString.getItemRef(5)

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

    @Test
    fun testStringConversion() {
        assertEquals("listOfString.@5", this.reference.completeName)

        val converted = TestMarykObject.getPropertyReferenceByName(this.reference.completeName)
        assertEquals(this.reference, converted)
    }
}