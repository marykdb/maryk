package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/** A Request to delete DataObjects for specific DataModel
 * @param dataModel Root model of data to remove objects from
 * @param objectsToDelete Array of object keys to delete object for
 * @param hardDelete false means data will still exist but be not requestable
 * and true will mean the data will be totally deleted
 */
data class DeleteRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val objectsToDelete: List<Key<DO>>,
        val hardDelete: Boolean = false
) : IsObjectRequest<DO, DM> {
    constructor(
            dataModel: DM, vararg objectToDelete: Key<DO>, hardDelete: Boolean
    ) : this(dataModel, objectToDelete.toList(), hardDelete)

    internal object Properties : PropertyDefinitions<DeleteRequest<*, *>>() {
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
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, DeleteRequest<*, *>::dataModel),
                    Def(Properties.objectsToDelete, DeleteRequest<*, *>::objectsToDelete),
                    Def(Properties.hardDelete, DeleteRequest<*,*>::hardDelete)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DeleteRequest(
                dataModel = map[0] as RootDataModel<Any>,
                objectsToDelete = map[1] as List<Key<Any>>,
                hardDelete = map[2] as Boolean
        )
    }
}