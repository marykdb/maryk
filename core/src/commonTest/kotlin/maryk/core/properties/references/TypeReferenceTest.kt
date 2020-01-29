package maryk.core.properties.references

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.TypeReferenceTest.MarykModel.Properties.multi
import maryk.core.properties.types.TypedValue
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.assertType
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.expect

internal class TypeReferenceTest {
    object MarykModel : RootDataModel<MarykModel, MarykModel.Properties>(
        keyDefinition = multi.typeRef(),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val multi by define(1u) {
                MultiTypeDefinition(
                    final = true,
                    typeEnum = MarykTypeEnum
                )
            }
        }

        operator fun invoke(
            multi: TypedValue<MarykTypeEnum<*>, *>
        ) = this.values {
            mapNonNulls(
                this.multi with multi
            )
        }
    }

    private val typeReference =
        TestMarykModel { multi.refToType() }

    @Test
    fun cacheReferenceTest() {
        assertSame(typeReference, TestMarykModel { multi.refToType() })
    }


    @Test
    fun testKey() {
        val obj = MarykModel(
            multi = TypedValue(T2, 23)
        )

        val key = MarykModel.key(obj)
        expect("0002") { key.bytes.toHex() }

        val keyDef = MarykModel.keyDefinition

        val specificDef = assertType<TypeReference<MarykTypeEnum<*>, *, *>>(keyDef)
        expect(multi.typeRef()) { specificDef }

        expect(T2) { specificDef.getValue(obj) }

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(T1, bc::write)
        expect(T1) { specificDef.readStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0a09") { multi.typeRef().toReferenceStorageByteArray().toHex() }
    }
}
