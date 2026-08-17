package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.FlowUpdateEmitter

/**
 * Processes the initial changes to values into the data store
 */
internal suspend fun <DM : IsRootDataModel> processInitialChangesUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateSharedFlow: FlowUpdateEmitter
) {
    val dataModel = storeAction.request.dataModel
    val dataStore = dataStoreFetcher.invoke(dataModel)

    val update = storeAction.request.update as InitialChangesUpdate<DM>

    val changeStatuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()
    for (change in update.changes) {
        for (versionedChange in change.changes) {
            val version = HLC(versionedChange.version)
            val lastAppliedVersion = dataStore.lastAppliedVersion(change.key.bytes)
            if (lastAppliedVersion != null && version <= lastAppliedVersion) {
                changeStatuses += if (versionedChange.changes.contains(ObjectCreate)) {
                    AddSuccess(change.key, lastAppliedVersion.timestamp, emptyList())
                } else {
                    ChangeSuccess(lastAppliedVersion.timestamp, emptyList())
                }
                continue
            }
            if (versionedChange.changes.contains(ObjectCreate)) {
                val addedValues = dataModel.fromChanges(null, versionedChange.changes)

                changeStatuses += processAdd(
                    dataStore,
                    dataModel = dataModel,
                    key = change.key,
                    version = version,
                    objectToAdd = addedValues,
                    updateSharedFlow = updateSharedFlow
                )
            } else {
                changeStatuses += processChange(
                    dataStore,
                    dataModel,
                    change.key,
                    null,
                    versionedChange.changes,
                    version,
                    updateSharedFlow
                )
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
