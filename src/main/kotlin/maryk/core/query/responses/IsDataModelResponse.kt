package maryk.core.query.responses

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.query.DataModelPropertyContext

/** A response for a data operation on a DataModel */
interface IsDataModelResponse<DO: Any, out DM: RootDataModel<DO>>{
    val dataModel: DM

    object Properties {
        val dataModel = ContextCaptureDefinition(
                ContextualModelReferenceDefinition<DataModelPropertyContext>(
                        name = "dataModelName",
                        index = 0,
                        contextualResolver = { context, name ->
                            context!!.dataModels[name]!!
                        }
                ), { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context!!.dataModel = value as RootDataModel<Any>
                }
        )
    }
}