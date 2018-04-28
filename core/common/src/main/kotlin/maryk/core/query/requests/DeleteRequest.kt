package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext


/**
 * Creates a Request to delete [objectsToDelete] from [dataModel]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.delete(
    vararg objectsToDelete: Key<DO>,
    hardDelete: Boolean = false
) = DeleteRequest(this, objectsToDelete.toList(), hardDelete)

/**
 * A Request to delete [objectsToDelete] from [dataModel]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
data class DeleteRequest<DO: Any, out DM: RootDataModel<DO, *>> internal constructor(
    override val dataModel: DM,
    val objectsToDelete: List<Key<DO>>,
    val hardDelete: Boolean
) : IsObjectRequest<DO, DM> {
    internal companion object: QueryDataModel<DeleteRequest<*, *>>(
        properties = object : PropertyDefinitions<DeleteRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, DeleteRequest<*, *>::dataModel)

                add(1, "objectsToDelete", ListDefinition(
                    valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel?.key ?: throw ContextNotFoundException()
                        }
                    )
                ),DeleteRequest<*, *>::objectsToDelete)

                add(2, "hardDelete", BooleanDefinition(), DeleteRequest<*,*>::hardDelete)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DeleteRequest(
            dataModel = map[0] as RootDataModel<Any, *>,
            objectsToDelete = map[1] as List<Key<Any>>,
            hardDelete = map[2] as Boolean? ?: false
        )
    }
}
