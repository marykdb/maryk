package maryk.core.properties.references

import maryk.Option.V1
import maryk.TestMarykModel
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.types.TypedValue
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class TypeReferenceTest {
    private val typeReference = TypeReference(
        type = V1,
        multiTypeDefinition = TestMarykModel.properties.multi.definition,
        parentReference = null
    )

    @Test
    fun get_value_from_map() {
        val typedValue = TypedValue(
            V1,
            "string"
        )

        this.typeReference.resolveFromAny(typedValue) shouldBe "string"

        shouldThrow<UnexpectedValueException> {
            this.typeReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun testCompleteName() {
        typeReference.completeName shouldBe "*V1"
    }
}
