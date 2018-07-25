package maryk.core.properties.references

import maryk.TestMarykModel
import maryk.core.protobuf.WriteCache
import maryk.lib.time.Date
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class SetItemReferenceTest {
    val reference = TestMarykModel.Properties.set.getItemRef(Date(2001, 4, 2))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
    }

    @Test
    fun testStringConversion() {
        this.reference.completeName shouldBe "set.$2001-04-02"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }
}
