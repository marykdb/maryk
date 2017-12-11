package maryk.core.properties.references

import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.Date
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe
import kotlin.test.Test

class SetItemReferenceTest {
    val reference = TestMarykObject.Properties.set.getItemRef(Date(2001, 4, 2))

    @Test
    fun testProtoBufConversion() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
                this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
    }

    @Test
    fun testStringConversion() {
        this.reference.completeName shouldBe "set.$2001-04-02"

        val converted = TestMarykObject.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }
}