package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction

/** Processes a Removal UpdateResponse into FoundationDB */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processDeleteUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    cache: Cache,
) {
    val dataModel = storeAction.request.dataModel
    val update = storeAction.request.update as RemovalUpdate<DM>

    if (update.reason == RemovalReason.NotInRange) {
        // Same semantics as memory/hbase: reject NotInRange deletes for processing
        throw maryk.core.exceptions.RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
    }

    val dbIndex = getDataModelId(dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val deleteStatus = processDelete(
        tableDirs = tableDirs,
        dataModel = dataModel,
        key = update.key,
        version = HLC(update.version),
        dbIndex = dbIndex,
        hardDelete = update.reason == RemovalReason.HardDelete,
        cache = cache,
    )

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            DeleteResponse(
                dataModel,
                listOf(deleteStatus)
            )
        )
    )
}

