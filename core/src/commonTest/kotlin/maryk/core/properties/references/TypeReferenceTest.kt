package maryk.core.properties.references

import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.query.ContainsDefinitionsContext
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.expect

internal class TypeReferenceTest {
    object MarykModel : RootDataModel<MarykModel>(
        keyDefinition = {
            MarykModel.multi.typeRef()
        },
    ) {
        val multi by multiType(
            index = 1u,
            typeEnum = MarykTypeEnum,
            final = true
        )
    }

    private val typeReference =
        TestMarykModel { multi.refToType() }

    @Test
    fun cacheReferenceTest() {
        assertSame(typeReference, TestMarykModel { multi.refToType() })
    }


    @Test
    fun testKey() {
        val obj = MarykModel.create{
            multi with T2(23)
        }

        val key = MarykModel.key(obj)
        expect("0002") { key.bytes.toHex() }

        val keyDef = MarykModel.Meta.keyDefinition

        val specificDef = assertIs<TypeReference<MarykTypeEnum<*>, *, *>>(keyDef)
        expect(MarykModel.multi.typeRef()) { specificDef }

        expect(T2) { specificDef.getValue(obj) }

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(T1, bc::write)
        expect(T1) { specificDef.readStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0a09") { MarykModel.multi.typeRef().referenceStorageByteArray.toHex() }
    }

    @Test
    fun compatibleWithModel() {
        assertTrue {
            typeReference.isCompatibleWithModel(TestMarykModel)
        }

        // Property definition wrapper which does not exist on Model
        val invalid = MultiTypeDefinitionWrapper<MarykTypeEnum<*>, Any, TypedValue<MarykTypeEnum<*>, Any>, ContainsDefinitionsContext, Any>(
            300u,
            "invalid",
            MultiTypeDefinition(typeEnum = MarykTypeEnum)
        )

        val invalidRef = invalid.typeRef()
        assertFalse {
            invalidRef.isCompatibleWithModel(MarykModel)
        }
    }
}
