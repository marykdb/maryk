package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.lib.time.Date
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class SetItemReferenceTest {
    val reference = TestMarykModel.Properties.set.getItemRef(Date(2001, 4, 2))

    @Test
    fun get_value_from_set() {
        val list = setOf(
            Date(2001, 4, 2),
            Date(2005, 5, 22),
            Date(2013, 10, 1)
        )

        this.reference.resolveFromAny(list) shouldBe Date(2001, 4, 2)

        shouldThrow<UnexpectedValueException> {
            this.reference.resolveFromAny("wrongInput")
        }
    }

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
    fun convert_to_and_from_string() {
        this.reference.completeName shouldBe "set.$2001-04-02"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }
}
