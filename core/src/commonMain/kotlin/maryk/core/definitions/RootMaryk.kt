package maryk.core.definitions

import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.Requests
import maryk.core.values.ObjectValues

/** Root Maryk element for multiple definitions or requests */
data class RootMaryk(
    val operations: List<TypedValue<Operation, *>> = listOf()
) {
    object Properties : ObjectPropertyDefinitions<RootMaryk>() {
        val operations = add(1, "operations",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = Operation,
                    definitionMap = mapOf(
                        Operation.Define to ContextTransformerDefinition<Definitions, DefinitionsContext, DefinitionsConversionContext>(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { Definitions }
                            ),
                            contextTransformer = {
                                it?.let { modelContext ->
                                    DefinitionsConversionContext(modelContext)
                                }
                            }
                        ),
                        Operation.Request to ContextTransformerDefinition<Requests, DefinitionsContext, RequestContext>(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { Requests }
                            ),
                            contextTransformer = {
                                it?.let { modelContext ->
                                    RequestContext(modelContext)
                                }
                            }
                        )
                    )
                )
            ),
            RootMaryk::operations
        )
    }

    @Suppress("UNCHECKED_CAST")
    companion object :
        SingleTypedValueDataModel<List<TypedValue<Operation, *>>, RootMaryk, Properties, DefinitionsContext>(
            properties = Properties,
            singlePropertyDefinition = Properties.operations as IsPropertyDefinitionWrapper<List<TypedValue<Operation, *>>, List<TypedValue<Operation, *>>, DefinitionsContext, RootMaryk>
        ) {
        override fun invoke(values: ObjectValues<RootMaryk, Properties>) = RootMaryk(
            operations = values(1)
        )
    }
}
