package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe
import kotlin.test.Test

class ListItemReferenceTest {
    val reference = TestMarykObject.Properties.listOfString.getItemRef(5)
    val cache = WriteCache()

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()

        bc.reserve(
            this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
    }

    @Test
    fun testStringConversion() {
        this.reference.completeName shouldBe "listOfString.@5"

        val converted = TestMarykObject.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }
}
