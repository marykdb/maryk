package maryk.core.properties.references

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.TypeReferenceTest.MarykModel.Properties.multi
import maryk.core.properties.types.TypedValue
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
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
                    typeEnum = MarykTypeEnum
                )
            )
        }

        operator fun invoke(
            multi: TypedValue<MarykTypeEnum<*>, *>
        ) = this.values {
            mapNonNulls(
                this.multi with multi
            )
        }
    }

    @Test
    fun testKey() {
        val obj = MarykModel(
            multi = TypedValue(T2, 23)
        )

        val key = MarykModel.key(obj)
        key.bytes.toHex() shouldBe "0002"

        val keyDef = MarykModel.keyDefinition

        val specificDef = shouldBeOfType<TypeReference<MarykTypeEnum<*>, *, *>>(keyDef)
        specificDef shouldBe multi.typeRef()

        specificDef.getValue(obj) shouldBe T2

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(T1, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe T1
    }

    @Test
    fun toReferenceStorageBytes() {
        multi.typeRef().toReferenceStorageByteArray().toHex() shouldBe "0a09"
    }
}
