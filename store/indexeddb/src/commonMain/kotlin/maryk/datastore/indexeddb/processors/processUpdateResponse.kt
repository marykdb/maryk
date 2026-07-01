package maryk.datastore.indexeddb.processors

import kotlinx.coroutines.CompletableDeferred
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.Values
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processUpdateResponse(
    storeAction: ProcessUpdateResponseStoreAction<DM>,
) {
    val dataModel = storeAction.request.dataModel
    when (val update = storeAction.request.update) {
        is AdditionUpdate<DM> -> {
            if (update.firstVersion != update.version) {
                throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
            }

            val response = CompletableDeferred<AddResponse<DM>>()
            processAddRequest(
                version = HLC(update.version),
                storeAction = StoreAction(
                    request = dataModel.add(update.key to update.values),
                    response = response,
                )
            )
            storeAction.response.complete(ProcessResponse(update.version, response.await()))
        }
        is ChangeUpdate<DM> -> {
            if (update.changes.contains(ObjectCreate)) {
                val response = CompletableDeferred<AddResponse<DM>>()
                processAddRequest(
                    version = HLC(update.version),
                    storeAction = StoreAction(
                        request = dataModel.add(update.key to dataModel.fromChanges(null, update.changes)),
                        response = response,
                    )
                )
                storeAction.response.complete(ProcessResponse(update.version, response.await()))
            } else {
                val response = CompletableDeferred<ChangeResponse<DM>>()
                processChangeRequest(
                    version = HLC(update.version),
                    storeAction = StoreAction(
                        request = dataModel.change(update.key.change(*update.changes.toTypedArray())),
                        response = response,
                    )
                )
                storeAction.response.complete(ProcessResponse(update.version, response.await()))
            }
        }
        is RemovalUpdate<DM> -> {
            if (update.reason == NotInRange) {
                throw RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
            }

            val response = CompletableDeferred<DeleteResponse<DM>>()
            processDeleteRequest(
                version = HLC(update.version),
                storeAction = StoreAction(
                    request = dataModel.delete(update.key, hardDelete = update.reason == HardDelete),
                    response = response,
                )
            )
            storeAction.response.complete(ProcessResponse(update.version, response.await()))
        }
        is InitialChangesUpdate<DM> -> {
            val statuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()
            for (change in update.changes) {
                for (versionedChange in change.changes) {
                    if (versionedChange.changes.contains(ObjectCreate)) {
                        val response = CompletableDeferred<AddResponse<DM>>()
                        processAddRequest(
                            version = HLC(versionedChange.version),
                            storeAction = StoreAction(
                                request = dataModel.add(change.key to dataModel.fromChanges(null, versionedChange.changes)),
                                response = response,
                            )
                        )
                        statuses += response.await().statuses
                    } else {
                        val response = CompletableDeferred<ChangeResponse<DM>>()
                        processChangeRequest(
                            version = HLC(versionedChange.version),
                            storeAction = StoreAction(
                                request = dataModel.change(change.key.change(*versionedChange.changes.toTypedArray())),
                                response = response,
                            )
                        )
                        statuses += response.await().statuses
                    }
                }
            }

            storeAction.response.complete(
                ProcessResponse(
                    update.version,
                    AddOrChangeResponse(dataModel, statuses)
                )
            )
        }
        is InitialValuesUpdate<DM> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
        is OrderedKeysUpdate<DM> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
        else -> throw TypeException("Unknown update type $update for datastore processing")
    }
}

