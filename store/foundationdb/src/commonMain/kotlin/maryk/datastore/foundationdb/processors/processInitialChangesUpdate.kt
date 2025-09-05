package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction

/** Processes an InitialChanges UpdateResponse into FoundationDB */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processInitialChangesUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel
    val update = storeAction.request.update as InitialChangesUpdate<DM>

    val tableDirs = getTableDirs(getDataModelId(dataModel))

    val changeStatuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()

    for (change in update.changes) {
        for (versionedChange in change.changes) {
            if (versionedChange.changes.contains(ObjectCreate)) {
                val addedValues = dataModel.fromChanges(null, versionedChange.changes)

                val addStatus = processAdd(
                    tableDirs = tableDirs,
                    dataModel = dataModel,
                    key = change.key,
                    version = HLC(versionedChange.version),
                    objectToAdd = addedValues,
                )
                changeStatuses += addStatus
            } else {
                val changeStatus = processChange(
                    dataModel = dataModel,
                    key = change.key,
                    lastVersion = null,
                    changes = versionedChange.changes,
                    version = HLC(versionedChange.version),
                    tableDirs = tableDirs,
                )
                changeStatuses += changeStatus
            }
        }
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            AddOrChangeResponse(
                dataModel,
                changeStatuses
            )
        )
    )
}

