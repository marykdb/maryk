package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
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
    override val requestType = RequestType.Delete

    internal companion object: SimpleQueryDataModel<DeleteRequest<*, *>>(
        properties = object : PropertyDefinitions<DeleteRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, DeleteRequest<*, *>::dataModel)

                add(1, "objectsToDelete", ListDefinition(
                    valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel?.key ?: throw ContextNotFoundException()
                        }
                    )
                ), DeleteRequest<*, *>::objectsToDelete)

                add(2, "hardDelete",
                    BooleanDefinition(default = false),
                    DeleteRequest<*,*>::hardDelete
                )
            }
        }
    ) {
        override fun invoke(map: SimpleValues<DeleteRequest<*, *>>) = DeleteRequest(
            dataModel = map<RootDataModel<Any, *>>(0),
            objectsToDelete = map(1),
            hardDelete = map(2)
        )
    }
}
