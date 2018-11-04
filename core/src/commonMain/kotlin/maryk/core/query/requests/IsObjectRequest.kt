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
interface IsObjectRequest<out DM: IsRootDataModel<*>, RP: IsResponse>: IsRequest<RP> {
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> IsRootDataModel<*>?) =
            definitions.add(
                1, "dataModel",
                ContextualModelReferenceDefinition<IsRootDataModel<*>, RequestContext>(
                    contextualResolver = { context, name ->
                        context?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.dataModels[name] as (Unit.() -> IsRootDataModel<*>)? ?: throw DefNotFoundException("DataModel of name $name not found on dataModels")
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                toSerializable = { value, _ ->
                    value?.let {
                        DataModelReference(it.name){ it }
                    }
                },
                fromSerializable = { it?.get?.invoke(Unit) },
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.dataModel = value.get(Unit) as IsRootDataModel<IsPropertyDefinitions>
                }
            )
    }
}