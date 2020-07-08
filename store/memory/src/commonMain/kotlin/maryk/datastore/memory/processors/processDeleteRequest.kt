package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.Update.Deletion
import maryk.lib.extensions.compare.compareTo
import kotlin.native.concurrent.SharedImmutable

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

@SharedImmutable
internal val objectSoftDeleteQualifier = byteArrayOf(0)

/** Processes a DeleteRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteRequest(
    version: HLC,
    storeAction: DeleteStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        @Suppress("UNCHECKED_CAST")
        val dataStore = dataStoreFetcher(deleteRequest.dataModel) as DataStore<DM, P>

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (deleteRequest.hardDelete && dataStore.keepAllVersions) {
            HistoricStoreIndexValuesWalker
        } else null

        for (key in deleteRequest.keys) {
            try {
                val index = dataStore.records.binarySearch { it.key.compareTo(key) }

                val status: IsDeleteResponseStatus<DM> = when {
                    index > -1 -> {
                        val objectToDelete = dataStore.records[index]
                        dataStore.removeFromUniqueIndices(objectToDelete, version, deleteRequest.hardDelete)

                        // Delete indexed values
                        deleteRequest.dataModel.indices?.forEach { indexable ->
                            val oldValue = indexable.toStorageByteArrayForIndex(objectToDelete, objectToDelete.key.bytes)
                            val indexRef = indexable.referenceStorageByteArray.bytes
                            if (oldValue != null) {
                                dataStore.removeFromIndex(
                                    objectToDelete,
                                    indexRef,
                                    version,
                                    oldValue
                                )
                            } // else ignore since did not exist

                            // Delete all historic values if historicStoreIndexValuesWalker was set
                            historicStoreIndexValuesWalker?.walkHistoricalValuesForIndexKeys(objectToDelete, indexable) { value, _ ->
                                dataStore.deleteHardFromIndex(
                                    indexRef,
                                    value,
                                    objectToDelete
                                )
                            }
                        }

                        if (deleteRequest.hardDelete) {
                            dataStore.records.removeAt(index)
                        } else {
                            val oldRecord = dataStore.records[index]
                            val newValues = oldRecord.values.toMutableList()

                            val valueIndex = oldRecord.values.binarySearch {
                                it.reference.compareTo(objectSoftDeleteQualifier)
                            }
                            setValueAtIndex(
                                newValues,
                                valueIndex,
                                objectSoftDeleteQualifier,
                                true,
                                version,
                                dataStore.keepAllVersions
                            )

                            val newRecord = oldRecord.copy(
                                values = newValues
                            )
                            dataStore.records[index] = newRecord
                        }
                        updateSendChannel.send(
                            Deletion(deleteRequest.dataModel, key, version.timestamp, deleteRequest.hardDelete)
                        )
                        DeleteSuccess(version.timestamp)
                    }
                    else -> DoesNotExist(key)
                }

                statuses.add(status)
            } catch (e: Throwable) {
                statuses.add(ServerFail(e.toString(), e))
            }
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
