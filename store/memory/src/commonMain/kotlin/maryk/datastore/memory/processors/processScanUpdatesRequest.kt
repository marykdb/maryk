package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction

internal typealias ScanUpdatesStoreAction<DM, P> = StoreAction<DM, P, ScanUpdatesRequest<DM, P>, UpdatesResponse<DM, P>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanUpdatesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>
) {
    val scanRequest = storeAction.request

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(scanRequest.dataModel) as DataStore<DM, P>

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM, P>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    processScan(
        scanRequest = scanRequest,
        dataStore = dataStore,
        recordFetcher = recordFetcher,
        scanSetup = {
            (it as? IndexScan)?.let { indexScan ->
                sortingKeys = mutableListOf()
                sortingIndex = indexScan.index
            }
        }
    ) { record ->
        insertionIndex++

        matchingKeys.add(record.key)

        // Add sorting index
        sortingIndex?.toStorageByteArrayForIndex(record, record.key.bytes)?.let {
            sortingKeys?.add(it)
        }

        lastResponseVersion = maxOf(lastResponseVersion, record.lastVersion.timestamp)

        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            scanRequest.fromVersion,
            scanRequest.toVersion,
            record
        )?.let { objectChange ->
            updates += objectChange.changes.mapNotNull { versionedChange ->
                val changes = versionedChange.changes

                if (changes.contains(ObjectCreate)) {
                    val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                    AdditionUpdate(
                        objectChange.key,
                        versionedChange.version,
                        insertionIndex,
                        addedValues
                    )
                } else {
                    if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                        ChangeUpdate(
                            objectChange.key,
                            versionedChange.version,
                            insertionIndex,
                            changes
                        )
                    } else {
                        scanRequest.dataModel.recordToValueWithMeta(
                            scanRequest.select,
                            scanRequest.toVersion?.let { HLC(it) },
                            record
                        )?.let { valuesWithMeta ->
                            AdditionUpdate(
                                objectChange.key,
                                versionedChange.version,
                                insertionIndex,
                                valuesWithMeta.values
                            )
                        }
                    }
                }
            }
        }
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys,
            sortingKeys = sortingKeys?.map { Bytes(it) }
        )
    )

    scanRequest.orderedKeys?.let { orderedKeys ->
        // Remove values which should or should not be there from passed orderedKeys
        // This so the requester is up to date with any in between filtered values
        orderedKeys.subtract(matchingKeys).let { removedKeys ->
            for (removedKey in removedKeys) {
                @Suppress("UNCHECKED_CAST")
                val record = recordFetcher(scanRequest.dataModel, removedKey)

                updates += RemovalUpdate(
                    key = removedKey,
                    version = lastResponseVersion,
                    reason = when {
                        record == null -> HardDelete
                        record.isDeleted(scanRequest.toVersion?.let { HLC(it) }) -> SoftDelete
                        else -> NotInRange
                    }
                )
            }
        }

        matchingKeys.subtract(orderedKeys).let { addedKeys ->
            for (addedKey in addedKeys) {
                @Suppress("UNCHECKED_CAST")
                val record = recordFetcher(scanRequest.dataModel, addedKey) as DataRecord<DM, P>?

                if (record != null) {
                    scanRequest.dataModel.recordToValueWithMeta(
                        scanRequest.select,
                        scanRequest.toVersion?.let { HLC(it) },
                        record
                    )?.let { valuesWithMeta ->
                        updates += AdditionUpdate(
                            record.key,
                            lastResponseVersion,
                            matchingKeys.indexOf(record.key),
                            valuesWithMeta.values
                        )
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates
        )
    )
}
