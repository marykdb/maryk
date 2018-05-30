package maryk.core.definitions

import maryk.core.objects.DataModel
import maryk.core.objects.QuerySingleValueDataModel
import maryk.core.objects.RootDataModel
import maryk.core.objects.ValueDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.IndexedEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.core.query.DataModelPropertyContext

data class Definitions(
    val definitions: List<MarykPrimitive>
) {
    constructor(vararg definition: MarykPrimitive): this(definition.toList())

    internal object Properties : PropertyDefinitions<Definitions>() {
        @Suppress("UNCHECKED_CAST")
        val definitions = add(0, "definitions",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = PrimitiveType,
                    definitionMap = mapOf(
                        PrimitiveType.Model to SubModelDefinition(
                            dataModel = { DataModel.Model }
                        ),
                        PrimitiveType.ValueModel to SubModelDefinition(
                            dataModel = { ValueDataModel.Model }
                        ),
                        PrimitiveType.RootModel to SubModelDefinition(
                            dataModel = { RootDataModel.Model }
                        ),
                        PrimitiveType.Enum to SubModelDefinition(
                            dataModel = { IndexedEnumDefinition.Model }
                        )
                    )
                ) as MultiTypeDefinition<PrimitiveType, DataModelPropertyContext>
            ),
            Definitions::definitions,
            fromSerializable = { it.value as MarykPrimitive },
            toSerializable = { TypedValue(it.primitiveType, it) }
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal companion object: QuerySingleValueDataModel<List<MarykPrimitive>, Definitions, DataModelContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.definitions as IsPropertyDefinitionWrapper<List<MarykPrimitive>, *, DataModelContext, Definitions>
    ) {
        override fun invoke(map: Map<Int, *>) = Definitions(
            definitions = map(0)
        )
    }
}
