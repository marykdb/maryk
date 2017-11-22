package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.query.DataModelPropertyContext

/** A Request for Data Objects
 * @param dataModel Root model of data to do operations on
 */
abstract class AbstractModelRequest<DO: Any, out DM: RootDataModel<DO>>(
        val dataModel: DM
) {
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