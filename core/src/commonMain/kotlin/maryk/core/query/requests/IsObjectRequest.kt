package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValuesPropertyDefinitions
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
        definition = ContextualModelReferenceDefinition<IsRootDataModel<*>, RequestContext>(
            contextualResolver = { context, modelName ->
                context?.let {
                    @Suppress("UNCHECKED_CAST")
                    it.dataModels[modelName] as (Unit.() -> IsRootDataModel<*>)?
                        ?: throw DefNotFoundException("DataModel of name $modelName not found on dataModels")
                } ?: throw ContextNotFoundException()
            }
        ),
        getter = getter,
        toSerializable = { value, _ ->
            value?.let {
                DataModelReference(it.Model.name) { it.Model }
            }
        },
        fromSerializable = { it?.get?.invoke(Unit)?.properties as IsRootModel? },
        shouldSerialize = { it !is DataModelReference<*> },
        capturer = { context, value ->
            @Suppress("UNCHECKED_CAST")
            context.dataModel = (value.get(Unit) as IsRootDataModel<IsValuesPropertyDefinitions>).properties
        }
    )
