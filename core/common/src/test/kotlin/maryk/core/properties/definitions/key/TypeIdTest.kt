package maryk.core.properties.definitions.key

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.definitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class TypeIdTest {
    object MarykModel: RootDataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        keyDefinitions = definitions(
            TypeId(MarykModel.Properties.multi)
        ),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val multi = MarykModel.Properties.add(
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
        ) = this.map {
            ReversedTest.MarykModel.Properties.mapNonNulls(
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

        val keyDef = MarykModel.keyDefinitions[0]

        (keyDef is TypeId<*>) shouldBe true
        val specificDef = keyDef as TypeId<*>
        specificDef.reference shouldBe MarykModel.Properties.multi.getRef()

        specificDef.getValue(MarykModel, obj) shouldBe 2

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(1, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe 1
    }

    private val context = DataModelContext(
        propertyDefinitions = MarykModel.Properties
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(
            value = TypeId(MarykModel.Properties.multi.getRef()),
            dataModel = TypeId.Model,
            context = { context }
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(
            value = TypeId(MarykModel.Properties.multi.getRef()),
            dataModel = TypeId.Model,
            context = { context }
        )
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(
            value = TypeId(MarykModel.Properties.multi.getRef()),
            dataModel = TypeId.Model,
            context = { context }
        )
    }
}
