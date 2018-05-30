package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.DataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.query.DataModelPropertyContext

/** A request for a data operation */
interface IsObjectRequest<DO: Any, out DM: RootDataModel<DO, *>>: IsRequest {
    val dataModel: DM

    companion object {
        internal fun <DM: Any> addDataModel(definitions: PropertyDefinitions<DM>, getter: (DM) -> RootDataModel<*, *>?) {
            definitions.add(
                0, "dataModel",
                ContextualModelReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = { context, name ->
                        context?.let {
                            it.dataModels[name]?.invoke() ?: throw DefNotFoundException("DataModel of name $name not found on dataModels")
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                toSerializable = { it: DataModel<*, *>? ->
                    it?.let{
                        { it }
                    }
                },
                fromSerializable = { it?.invoke() },
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.dataModel = value() as RootDataModel<Any, PropertyDefinitions<Any>>
                }
            )
        }
    }
}
