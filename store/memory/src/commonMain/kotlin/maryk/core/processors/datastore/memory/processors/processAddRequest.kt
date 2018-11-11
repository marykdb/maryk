@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.memory.StoreAction
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.processors.datastore.memory.records.toDataRecordValueTree
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.lib.time.Instant

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processAddRequest(storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>, dataList: MutableList<DataRecord<*, *>>) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    val version = Instant.getCurrentEpochTimeInMillis().toULong()

    if (addRequest.objectsToAdd.isNotEmpty()) {
        for (objectToAdd in addRequest.objectsToAdd) {
            val key = addRequest.dataModel.key(objectToAdd)

            dataList.add(
                DataRecord(
                    firstVersion = version,
                    key = key,
                    values = objectToAdd.toDataRecordValueTree(version)
                )
            )
            statuses.add(
                AddSuccess(key, version, listOf())
            )
        }

        dataList.sortBy {
            it.key
        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
