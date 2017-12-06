package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.query.DataModelPropertyContext

/** A request for a data operation */
interface IsObjectRequest<DO: Any, out DM: RootDataModel<DO>>{
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: PropertyDefinitions<DM>, getter: (DM) -> RootDataModel<*>?) {
            definitions.add(
                    0, "dataModel",
                    ContextCaptureDefinition(
                            ContextualModelReferenceDefinition<DataModelPropertyContext>(
                                    contextualResolver = { context, name ->
                                        context!!.dataModels[name]!!
                                    }
                            )
                    ) { context, value ->
                            @Suppress("UNCHECKED_CAST")
                            context!!.dataModel = value as RootDataModel<Any>
                    },
                    getter
            )
        }
    }
}