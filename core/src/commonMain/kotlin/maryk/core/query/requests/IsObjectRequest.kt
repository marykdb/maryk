package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse

/** A request for a data operation */
interface IsObjectRequest<out DM : IsRootDataModel<*>, RP : IsResponse> : IsRequest<RP> {
    val dataModel: DM

    companion object {
        internal fun <DM : Any> addDataModel(
            name: String,
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> IsRootDataModel<*>?
        ) =
            definitions.add(
                1u, name,
                ContextualModelReferenceDefinition<IsRootDataModel<*>, RequestContext>(
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
                        DataModelReference(it.name) { it }
                    }
                },
                fromSerializable = { it?.get?.invoke(Unit) },
                shouldSerialize = { it !is DataModelReference<*> },
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.dataModel = value.get(Unit) as IsRootDataModel<IsPropertyDefinitions>
                }
            )
    }
}
