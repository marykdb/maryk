package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class ListItemReferenceTest {
    val reference = TestMarykModel.Properties.listOfString.getItemRef(5)
    val cache = WriteCache()

    @Test
    fun get_value_from_list() {
        val list = listOf('a','b','c','d','e','f', 'g')

        this.reference.resolveFromAny(list) shouldBe 'f'

        shouldThrow<UnexpectedValueException> {
            this.reference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()

        bc.reserve(
            this.reference.calculateTransportByteLength(cache)
        )
        this.reference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe this.reference
    }

    @Test
    fun convert_to_and_from_string() {
        this.reference.completeName shouldBe "listOfString.@5"

        val converted = TestMarykModel.getPropertyReferenceByName(this.reference.completeName)
        converted shouldBe this.reference
    }
}
