package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update.Deletion
import maryk.lib.extensions.compare.compareTo

/**
 * Processed the deletion of the value at [key]/[version] from the in memory data store
 */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDelete(
    dataStore: DataStore<DM, P>,
    dataModel: DM,
    key: Key<DM>,
    version: HLC,
    hardDelete: Boolean,
    historicStoreIndexValuesWalker: HistoricStoreIndexValuesWalker?,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
) : IsDeleteResponseStatus<DM> {
    val index = dataStore.records.binarySearch { it.key.compareTo(key) }

    return when {
        index > -1 -> {
            val objectToDelete = dataStore.records[index]
            dataStore.removeFromUniqueIndices(objectToDelete, version, hardDelete)

            // Delete indexed values
            dataModel.indices?.forEach { indexable ->
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

            if (hardDelete) {
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

            updateSharedFlow.emit(
                Deletion(dataModel, key, version.timestamp, hardDelete)
            )
            DeleteSuccess(version.timestamp)
        }
        else -> DoesNotExist(key)
    }
}
