package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.responses.DeleteResponse

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
) : IsObjectRequest<DM, DeleteResponse<*>> {
    override val requestType = RequestType.Delete
    override val responseModel = DeleteResponse

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<DeleteRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, DeleteRequest<*>::dataModel)

        val objectsToDelete = add(2, "objectsToDelete", ListDefinition(
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        ), DeleteRequest<*>::objectsToDelete)

        val hardDelete = add(3, "hardDelete",
            BooleanDefinition(default = false),
            DeleteRequest<*>::hardDelete
        )
    }

    companion object: QueryDataModel<DeleteRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<DeleteRequest<*>, Properties>) = DeleteRequest(
            dataModel = map(1),
            objectsToDelete = map(2),
            hardDelete = map(3)
        )
    }
}
