package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext


/**
 * Creates a Request to delete [objectsToDelete] from [DM]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
fun <DM: IsRootDataModel<*>> DM.delete(
    vararg objectsToDelete: Key<DM>,
    hardDelete: Boolean = false
) = DeleteRequest(this, objectsToDelete.toList(), hardDelete)

/**
 * A Request to delete [objectsToDelete] from [dataModel]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
data class DeleteRequest<out DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    val objectsToDelete: List<Key<DM>>,
    val hardDelete: Boolean
) : IsObjectRequest<DM> {
    override val requestType = RequestType.Delete

    internal companion object: SimpleQueryDataModel<DeleteRequest<*>>(
        properties = object : ObjectPropertyDefinitions<DeleteRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, DeleteRequest<*>::dataModel)

                add(2, "objectsToDelete", ListDefinition(
                    valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                        }
                    )
                ), DeleteRequest<*>::objectsToDelete)

                add(3, "hardDelete",
                    BooleanDefinition(default = false),
                    DeleteRequest<*>::hardDelete
                )
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<DeleteRequest<*>>) = DeleteRequest(
            dataModel = map(1),
            objectsToDelete = map(2),
            hardDelete = map(3)
        )
    }
}
