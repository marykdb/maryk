package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse

/** A request for a data operation */
interface IsObjectRequest<out DM : IsRootDataModel, RP : IsResponse> : IsRequest<RP> {
    val dataModel: DM
}

internal fun <DM : IsStoreRequest<*, *>> IsObjectDataModel<DM>.addDataModel(
    getter: (DM) -> IsRootDataModel?
) =
    this.contextual(
        index = 1u,
        definition = ContextualModelReferenceDefinition<IsRootDataModel, RequestContext>(
            contextualResolver = { context, modelName ->
                context?.let {
                    @Suppress("UNCHECKED_CAST")
                    it.dataModels[modelName] as? IsDataModelReference<IsRootDataModel>
                        ?: throw DefNotFoundException("DataModel of name $modelName not found on dataModels")
                } ?: throw ContextNotFoundException()
            }
        ),
        getter = getter,
        toSerializable = { value, _ ->
            value?.let {
                DataModelReference(it.Meta.name) { it }
            }
        },
        fromSerializable = { it?.get?.invoke() },
        shouldSerialize = { it !is DataModelReference<*> },
        capturer = { context, value ->
            context.dataModel = value.get()
        }
    )
