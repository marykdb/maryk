package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.key.TypeIdTest.MarykModel.Properties.multi
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.Option
import maryk.test.shouldBe
import kotlin.test.Test

internal class TypeIdTest {
    object MarykModel: RootDataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        keyDefinition = TypeId(multi.ref()),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val multi = add(
                1,
                "multi",
                MultiTypeDefinition(
                    final = true,
                    typeEnum = Option,
                    definitionMap = mapOf<Option, IsSubDefinition<*, IsPropertyContext>>(
                        Option.V1 to StringDefinition(),
                        Option.V2 to BooleanDefinition()
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
    fun testKey(){
        val obj = MarykModel(
            multi = TypedValue(Option.V2, true)
        )

        val key = MarykModel.key(obj)
        key.bytes.toHex() shouldBe "0002"

        val keyDef = MarykModel.keyDefinition

        (keyDef is TypeId<*>) shouldBe true
        val specificDef = keyDef as TypeId<*>
        specificDef.reference shouldBe multi.ref()

        specificDef.getValue(obj) shouldBe 2u

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(1u, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe 1u
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = MarykModel.Properties
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = TypeId(multi.ref()),
            dataModel = TypeId.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = TypeId(multi.ref()),
            dataModel = TypeId.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(
            value = TypeId(multi.ref()),
            dataModel = TypeId.Model,
            context = { context }
        ) shouldBe "multi"
    }

    @Test
    fun toReferenceStorageBytes() {
        TypeId(multi.ref()).toReferenceStorageByteArray().toHex() shouldBe "0b09"
    }
}
