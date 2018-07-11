package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DataModelPropertyContext

/** A request for a data operation */
interface IsObjectRequest<DO: Any, out DM: RootObjectDataModel<DO, *>>: IsRequest {
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> RootObjectDataModel<*, *>?) {
            definitions.add(
                0, "dataModel",
                ContextualModelReferenceDefinition<RootObjectDataModel<*, *>, DataModelPropertyContext>(
                    contextualResolver = { context, name ->
                        context?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.dataModels[name] as (() -> RootObjectDataModel<*, *>)? ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                toSerializable = { value, _ ->
                    value?.let{
                        DataModelReference(it.name){ it }
                    }
                },
                fromSerializable = { it?.get?.invoke() },
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.dataModel = value.get() as RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
                }
            )
        }
    }
}
