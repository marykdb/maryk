package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.query.DataModelPropertyContext

/** A request for a data operation */
interface IsObjectRequest<DO: Any, out DM: RootDataModel<DO, *>>{
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: PropertyDefinitions<DM>, getter: (DM) -> RootDataModel<*, *>?) {
            definitions.add(
                0, "dataModel",
                ContextCaptureDefinition(
                    ContextualModelReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = { context, name ->
                            context?.let {
                                it.dataModels[name] ?: throw DefNotFoundException("DataModel of name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    )
                ) { context, value ->
                    context?.apply{
                        @Suppress("UNCHECKED_CAST")
                        dataModel = value as RootDataModel<Any, PropertyDefinitions<Any>>
                    } ?: ContextNotFoundException()
                },
                getter
            )
        }
    }
}
