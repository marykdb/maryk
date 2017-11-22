package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.query.properties.DataModelPropertyContext

/** A Request to delete DataObjects for specific DataModel
 * @param dataModel Root model of data to remove objects from
 * @param objectsToDelete Array of object keys to delete object for
 * @param hardDelete false means data will still exist but be not requestable
 * and true will mean the data will be totally deleted
 */
class DeleteRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg val objectsToDelete: Key<DO>,
        val hardDelete: Boolean = false
) : AbstractModelRequest<DO, DM>(dataModel) {
    object Properties {
        val objectsToDelete = ListDefinition(
                name = "objectsToDelete",
                index = 1,
                required = true,
                valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!!.key }
                )
        )
        val hardDelete = BooleanDefinition(
                name = "hardDelete",
                index = 2
        )
    }

    companion object: QueryDataModel<DeleteRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                DeleteRequest(
                        dataModel = it[0] as RootDataModel<Any>,
                        objectsToDelete = *(it[1] as List<Key<Any>>).toTypedArray(),
                        hardDelete = it[2] as Boolean
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, DeleteRequest<*, *>::dataModel),
                    Def(Properties.objectsToDelete, {
                        @Suppress("UNCHECKED_CAST")
                        it.objectsToDelete.toList() as List<Key<Any>>
                    }),
                    Def(Properties.hardDelete, DeleteRequest<*,*>::hardDelete)
            )
    )
}