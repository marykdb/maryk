package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.normalizeReplicationChanges
import maryk.datastore.shared.fromReplicationChanges
import maryk.datastore.shared.updates.FlowUpdateEmitter

/**
 * Processes the changes to values into the data store
 */
internal suspend fun <DM : IsRootDataModel> processChangeUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateSharedFlow: FlowUpdateEmitter
) {
    val dataModel = storeAction.request.dataModel
    val dataStore = dataStoreFetcher.invoke(dataModel)

    val update = storeAction.request.update as ChangeUpdate<DM>

    val changes = normalizeReplicationChanges(update.changes)
    if (changes.contains(ObjectCreate)) {
        val version = HLC(update.version)
        val response = if (dataStore.lastAppliedVersion(update.key.bytes)?.let { version <= it } == true) {
            AddSuccess(update.key, update.version, emptyList())
        } else {
            val addedValues = dataModel.fromReplicationChanges(null, changes)
            processAdd(
                dataStore = dataStore,
                dataModel = dataModel,
                key = update.key,
                version = version,
                objectToAdd = addedValues,
                isDeleted = changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: false,
                updateSharedFlow = updateSharedFlow
            )
        }

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(response)))
        )
    } else {
        val version = HLC(update.version)
        val response = if (dataStore.lastAppliedVersion(update.key.bytes)?.let { version <= it } == true) {
            ChangeSuccess(update.version, emptyList())
        } else {
            processChange(
                dataStore,
                dataModel,
                update.key,
                null,
                changes,
                version,
                updateSharedFlow,
                advanceVersionOnNoOp = true,
            )
        }

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(response)))
        )
    }
}
