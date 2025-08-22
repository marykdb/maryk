package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processAddRequest(
    version: HLC,
    storeAction: StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val dbIndex = getDataModelId(addRequest.dataModel)
        val tableDirs = getTableDirs(dbIndex)

        // Process each object in its own FDB transaction
        for ((index, objectToAdd) in addRequest.objects.withIndex()) {
            val key = addRequest.keysForObjects?.getOrNull(index)
                ?: addRequest.dataModel.key(objectToAdd)

            val status = processAdd(tableDirs, addRequest.dataModel, key, version, objectToAdd)

            statuses += status
        }
    }

    storeAction.response.complete(
        AddResponse(
            addRequest.dataModel,
            statuses
        )
    )
}
