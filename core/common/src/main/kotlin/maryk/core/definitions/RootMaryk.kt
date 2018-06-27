package maryk.core.definitions

import maryk.core.models.QuerySingleValueDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.requests.Requests

/** Root Maryk element for multiple definitions or requests */
data class RootMaryk(
    val operations: List<TypedValue<Operation, *>> = listOf()
) {
    internal object Properties : PropertyDefinitions<RootMaryk>() {
        @Suppress("UNCHECKED_CAST")
        val operations = add(0, "operations",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = Operation,
                    definitionMap = mapOf(
                        Operation.Define to EmbeddedObjectDefinition(
                            dataModel = { Definitions }
                        ),
                        Operation.Request to ContextTransformerDefinition<Requests, DataModelContext, DataModelPropertyContext>(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { Requests }
                            ),
                            contextTransformer = {
                                it?.let { modelContext ->
                                    DataModelPropertyContext(modelContext.dataModels)
                                }
                            }
                        )
                    ) as Map<Operation, IsSubDefinition<out Any, DataModelContext>>
                )
            ),
            RootMaryk::operations
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal companion object: QuerySingleValueDataModel<List<TypedValue<Operation, *>>, RootMaryk, DataModelContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.operations as IsPropertyDefinitionWrapper<List<TypedValue<Operation, *>>, *, DataModelContext, RootMaryk>
    ) {
        override fun invoke(map: DataObjectMap<RootMaryk>) = RootMaryk(
            operations = map(0)
        )
    }
}
