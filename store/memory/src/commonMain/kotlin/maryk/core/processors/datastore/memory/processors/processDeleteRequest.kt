@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.StoreAction
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.processors.datastore.memory.records.DeleteState.Deleted
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.Success
import maryk.lib.time.Instant

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processDeleteRequest(
    storeAction: DeleteStoreAction<DM, P>,
    dataList: MutableList<DataRecord<DM, P>>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.objectsToDelete.isNotEmpty()) {
        val version = Instant.getCurrentEpochTimeInMillis().toULong()

        for (key in deleteRequest.objectsToDelete) {
            val index = dataList.binarySearch { it.key.compareTo(key) }

            val status: IsDeleteResponseStatus<DM> = when {
                index > -1 -> {
                    if (deleteRequest.hardDelete) {
                        dataList.removeAt(index)
                    } else {
                        val newRecord = dataList[index].copy(
                            isDeleted = Deleted(version)
                        )
                        dataList[index] = newRecord
                    }
                    Success(version)
                }
                else -> DoesNotExist(key)
            }

            statuses.add(status)
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
