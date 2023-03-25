package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsRootModel
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.requests.RequestType.Delete
import maryk.core.query.responses.DeleteResponse
import maryk.core.values.ObjectValues

/**
 * Creates a Request to delete [objectsToDelete] from [DM]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
fun <DM : IsRootModel> DM.delete(
    vararg objectsToDelete: Key<DM>,
    hardDelete: Boolean = false
) = DeleteRequest(this, objectsToDelete.toList(), hardDelete)

/**
 * A Request to delete [keys] from [dataModel]. If [hardDelete] is false the data will still exist but is
 * not possible to request from server.
 */
data class DeleteRequest<DM : IsRootModel> internal constructor(
    override val dataModel: DM,
    val keys: List<Key<DM>>,
    val hardDelete: Boolean
) : IsStoreRequest<DM, DeleteResponse<DM>>, IsTransportableRequest<DeleteResponse<DM>> {
    override val requestType = Delete
    override val responseModel = DeleteResponse

    companion object : QueryModel<DeleteRequest<*>, Companion>() {
        val from by addDataModel { it.dataModel }
        val keys by list(
            index = 2u,
            getter = DeleteRequest<*>::keys,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootModel ?: throw ContextNotFoundException()
                }
            )
        )

        val hardDelete by boolean(
            index = 3u,
            getter = DeleteRequest<*>::hardDelete,
            default = false
        )

        override fun invoke(values: ObjectValues<DeleteRequest<*>, Companion>) = DeleteRequest(
            dataModel = values(1u),
            keys = values(2u),
            hardDelete = values(3u)
        )
    }
}
