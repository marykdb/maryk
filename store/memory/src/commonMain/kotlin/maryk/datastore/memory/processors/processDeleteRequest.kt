package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.lib.extensions.compare.compareTo

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal val objectSoftDeleteQualifier = byteArrayOf(0)

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteRequest(
    storeAction: DeleteStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val version = storeAction.version

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
                            if (oldValue != null) {
                                dataStore.removeFromIndex(
                                    objectToDelete,
                                    indexable.toReferenceStorageByteArray(),
                                    version,
                                    oldValue
                                )
                            } // else ignore since did not exist
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
