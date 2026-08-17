package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.FlowUpdateEmitter
import maryk.datastore.shared.updates.Update.Deletion

/**
 * Processes the deletion of values from the data store
 */
internal suspend fun <DM : IsRootDataModel> processDeleteUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<DM>,
    updateSharedFlow: FlowUpdateEmitter
) {
    val dataModel = storeAction.request.dataModel
    val dataStore = dataStoreFetcher.invoke(dataModel)

    val update = storeAction.request.update as RemovalUpdate<DM>

    // Only delete from store
    val version = HLC(update.version)
    val response = if (update.reason !== NotInRange) {
        if (dataStore.lastAppliedVersion(update.key.bytes)?.let { version <= it } == true) {
            DeleteSuccess(update.version)
        } else {
            val result = processDelete(
                dataStore,
                dataModel,
                update.key,
                version,
                update.reason == HardDelete,
                updateSharedFlow
            )
            if (update.reason == HardDelete && result is DoesNotExist) {
                dataStore.recordHardDelete(update.key.bytes, version)
                dataStore.addToUpdateHistory(version, update.key.bytes, isHardDelete = true)
                updateSharedFlow(Deletion(dataModel, update.key, update.version, true))
                DeleteSuccess(update.version)
            } else {
                result
            }
        }
    } else {
        throw RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
    }

    storeAction.response.complete(
        ProcessResponse(
            update.version,
            DeleteResponse(
                dataModel,
                listOf(response)
            )
        )
    )
}
