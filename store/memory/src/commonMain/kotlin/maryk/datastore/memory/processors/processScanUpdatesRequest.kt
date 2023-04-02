package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.properties.IsRootModel
import maryk.core.properties.fromChanges
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
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootModel> processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<*>
) {
    val scanRequest = storeAction.request

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    @Suppress("UNCHECKED_CAST")
    val dataStore = (dataStoreFetcher as IsStoreFetcher<DM>).invoke(scanRequest.dataModel)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null

    var insertionIndex = -1

    processScan(
        scanRequest = scanRequest,
        dataStore = dataStore,
        recordFetcher = recordFetcher,
        scanSetup = {
            (it as? IndexScan)?.let { _ ->
                sortingKeys = mutableListOf()
            }
        }
    ) { record, sortingKey ->
        insertionIndex++

        matchingKeys.add(record.key)

        // Add sorting index
        sortingKey?.let {
            sortingKeys?.add(it)
        }

        lastResponseVersion = maxOf(lastResponseVersion, record.lastVersion.timestamp)

        scanRequest.checkMaxVersions(dataStore.keepAllVersions)

        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            scanRequest.fromVersion,
            scanRequest.toVersion,
            scanRequest.maxVersions,
            sortingKey,
            record
        )?.let { objectChange ->
            updates += objectChange.changes.mapNotNull { versionedChange ->
                val changes = versionedChange.changes

                if (changes.contains(ObjectCreate)) {
                    val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                    AdditionUpdate(
                        key = objectChange.key,
                        version = versionedChange.version,
                        firstVersion = versionedChange.version,
                        insertionIndex = insertionIndex,
                        isDeleted = false,
                        values = addedValues
                    )
                } else {
                    if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                        ChangeUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            index = insertionIndex,
                            changes = changes
                        )
                    } else {
                        scanRequest.dataModel.recordToValueWithMeta(
                            scanRequest.select,
                            scanRequest.toVersion?.let { HLC(it) },
                            record
                        )?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = insertionIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
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
        orderedKeys.subtract(matchingKeys.toSet()).let { removedKeys ->
            for (removedKey in removedKeys) {
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

        matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
            for (addedKey in addedKeys) {
                @Suppress("UNCHECKED_CAST")
                val record = recordFetcher(scanRequest.dataModel, addedKey) as DataRecord<DM>?

                if (record != null) {
                    scanRequest.dataModel.recordToValueWithMeta(
                        scanRequest.select,
                        scanRequest.toVersion?.let { HLC(it) },
                        record
                    )?.let { valuesWithMeta ->
                        updates += AdditionUpdate(
                            record.key,
                            lastResponseVersion,
                            valuesWithMeta.firstVersion,
                            matchingKeys.indexOf(record.key),
                            valuesWithMeta.isDeleted,
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
