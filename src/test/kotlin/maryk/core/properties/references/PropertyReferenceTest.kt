package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PropertyReferenceTest {
    private val modelDefinition = SubModelDefinition(
            index = 2,
            name = "subModel",
            dataModel = TestMarykObject
    )

    private val definition = StringDefinition(
            index = 1,
            name = "test"
    )

    private val ref = this.definition.getRef()
    private val subRef = this.definition.getRef({ modelDefinition.getRef() })

    @Test
    fun getCompleteName() {
        assertEquals("test", this.ref.completeName)
        assertEquals("subModel.test", this.subRef.completeName)
    }

    @Test
    fun testHashCode() {
        assertEquals(3556498, this.ref.hashCode())
    }

    @Test
    fun testCompareTo() {
        assertTrue { this.ref == definition.getRef() }
        assertFalse { this.ref == modelDefinition.getRef() }
    }

    @Test
    fun testProtoBufWrite() {
        val bc = ByteCollectorWithLengthCacher()

        bc.reserve(
            this.subRef.calculateTransportByteLength(bc::addToCache)
        )
        this.subRef.writeTransportBytes(bc::nextLengthFromCache, bc::write)

        assertEquals("0201", bc.bytes!!.toHex())
    }
}