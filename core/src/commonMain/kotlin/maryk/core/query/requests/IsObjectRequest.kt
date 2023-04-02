package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsRootModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse

/** A request for a data operation */
interface IsObjectRequest<out DM : IsRootModel, RP : IsResponse> : IsRequest<RP> {
    val dataModel: DM
}

internal fun <DM : IsStoreRequest<*, *>> ObjectPropertyDefinitions<DM>.addDataModel(
    getter: (DM) -> IsRootModel?
) =
    this.contextual(
        index = 1u,
        definition = ContextualModelReferenceDefinition<IsRootModel, RequestContext>(
            contextualResolver = { context, modelName ->
                context?.let {
                    @Suppress("UNCHECKED_CAST")
                    it.dataModels[modelName] as (Unit.() -> IsRootModel)?
                        ?: throw DefNotFoundException("DataModel of name $modelName not found on dataModels")
                } ?: throw ContextNotFoundException()
            }
        ),
        getter = getter,
        toSerializable = { value, _ ->
            value?.let {
                DataModelReference(it.Model.name) { it }
            }
        },
        fromSerializable = { it?.get?.invoke(Unit) },
        shouldSerialize = { it !is DataModelReference<*> },
        capturer = { context, value ->
            context.dataModel = value.get(Unit)
        }
    )
