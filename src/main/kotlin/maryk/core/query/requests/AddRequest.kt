package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualSubModelDefinition
import maryk.core.query.DataModelPropertyContext

/** A Request to add DataObjects for specific DataModel
 * @param dataModel Root model of data to add objects to
 * @param objectsToAdd Array of objects to add
 */
class AddRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg val objectsToAdd: DO
) : AbstractModelRequest<DO, DM>(dataModel) {
    object Properties {
        val objectsToAdd = ListDefinition(
                name = "objectsToAdd",
                index = 1,
                required = true,
                valueDefinition = ContextualSubModelDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!! }
                )
        )
    }

    companion object: QueryDataModel<AddRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                AddRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        objectsToAdd = *(it[1] as List<Any>).toTypedArray()
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, AddRequest<*, *>::dataModel),
                    Def(Properties.objectsToAdd, {
                        it.objectsToAdd.toList()
                    })
            )
    )
}
