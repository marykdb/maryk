package maryk.core.properties.definitions.key

import maryk.Option
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.objects.definitions
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import kotlin.test.Test

internal class TypeIdTest {
    private data class MarykObject(
        val multi: TypedValue<Option, *>
    ){
        object Properties : PropertyDefinitions<MarykObject>() {
            val multi = add(0, "multi", MultiTypeDefinition(
                definitionMap = mapOf<Option, IsSubDefinition<*, IsPropertyContext>>(
                    Option.V0 to StringDefinition(),
                    Option.V1 to BooleanDefinition()
                )
            ), MarykObject::multi)
        }
        companion object: RootDataModel<MarykObject, Properties>(
            name = "MarykObject",
            keyDefinitions = definitions(
                TypeId(Properties.multi)
            ),
            properties = Properties
        ) {
            @Suppress("UNCHECKED_CAST")
            override fun invoke(map: Map<Int, *>) = MarykObject(
                map[0] as TypedValue<Option, *>
            )
        }
    }

    @Test
    fun testKey(){
        val obj = MarykObject(
            multi = TypedValue(Option.V1, true)
        )

        val key = MarykObject.key(obj)
        key.bytes.toHex() shouldBe "0001"

        val keyDef = MarykObject.key.keyDefinitions[0]

        (keyDef is TypeId<*>) shouldBe true
        val specificDef = keyDef as TypeId<*>
        specificDef.multiTypeReference shouldBe MarykObject.Properties.multi.getRef()

        specificDef.getValue(MarykObject, obj) shouldBe 1

        val bc = ByteCollector()
        bc.reserve(2)
        specificDef.writeStorageBytes(1, bc::write)
        specificDef.readStorageBytes(bc.size, bc::read) shouldBe 1
    }

    private val context = DataModelContext(
        propertyDefinitions = MarykObject.Properties
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(
            value = TypeId(MarykObject.Properties.multi.getRef()),
            dataModel = TypeId.Model,
            context = context
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(
            value = TypeId(MarykObject.Properties.multi.getRef()),
            dataModel = TypeId.Model,
            context = context
        )
    }
}
