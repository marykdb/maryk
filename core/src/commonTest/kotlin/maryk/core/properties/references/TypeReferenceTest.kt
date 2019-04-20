package maryk.core.properties.references

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.TypeReferenceTest.MarykModel.Properties.multi
import maryk.core.properties.types.TypedValue
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.Option
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.shouldBe
import kotlin.test.Test

internal class TypeReferenceTest {
    object MarykModel : RootDataModel<MarykModel, MarykModel.Properties>(
        keyDefinition = multi.typeRef(),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val multi = add(
                1u,
                "multi",
                MultiTypeDefinition(
                    final = true,
                    typeEnum = Option,
                    definitionMap = mapOf<Option, IsSubDefinition<*, IsPropertyContext>>(
                        V1 to StringDefinition(),
                        V2 to BooleanDefinition()
                    )
                )
            )
        }

        operator fun invoke(
            multi: TypedValue<Option, *>
        ) = this.values {
            mapNonNulls(
                this.multi with multi
            )
        }
    }

    @Test
    fun testKey() {
        val obj = MarykModel(
            multi = TypedValue(V2, true)
        )

        val key = MarykModel.key(obj)
        key.bytes.toHex() shouldBe "0002"

        val keyDef = MarykModel.keyDefinition

        (keyDef is TypeReference<*, *>) shouldBe true
        @Suppress("UNCHECKED_CAST")
        val specificDef = keyDef as TypeReference<Option, *>
        specificDef shouldBe multi.typeRef()

        specificDef.getValue(obj) shouldBe V2

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(V1, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe V1
    }

    @Test
    fun toReferenceStorageBytes() {
        multi.typeRef().toReferenceStorageByteArray().toHex() shouldBe "0a09"
    }
}
