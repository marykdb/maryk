package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.getLastVersion
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias GetUpdatesStoreAction<DM, P> = StoreAction<DM, P, GetUpdatesRequest<DM, P>, UpdatesResponse<DM, P>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetUpdatesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(dataStore.keepAllVersions)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM, P>>()
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    DBAccessor(dataStore).use { dbAccessor ->
        val dbIndex = dataStore.getDataModelId(getRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        val columnToScan = if (getRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        keyWalk@ for (key in getRequest.keys) {
            val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)
            if (mayExist) {
                val valueLength =
                    dbAccessor.get(columnFamilies.keys, dataStore.defaultReadOptions, key.bytes, recyclableByteArray)

                if (valueLength != rocksDBNotFound) {
                    val creationVersion = recyclableByteArray.readVersionBytes()
                    if (getRequest.shouldBeFiltered(
                            dbAccessor,
                            columnFamilies,
                            dataStore.defaultReadOptions,
                            key.bytes,
                            0,
                            key.size,
                            creationVersion,
                            getRequest.toVersion
                        )
                    ) {
                        continue@keyWalk
                    }

                    val lastVersion = getLastVersion(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key)

                    insertionIndex++

                    matchingKeys.add(key)
                    lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        dataStore.readValueWithCache(dbIndex, key, reference, version, valueReader)
                    }

                    getRequest.dataModel.readTransactionIntoObjectChanges(
                        iterator,
                        creationVersion,
                        columnFamilies,
                        key,
                        getRequest.select,
                        getRequest.fromVersion,
                        getRequest.toVersion,
                        cacheReader
                    )?.also { objectChange ->
                        updates += objectChange.changes.map { versionedChange ->
                            val changes = versionedChange.changes

                            if (changes.contains(ObjectCreate)) {
                                val addedValues = getRequest.dataModel.fromChanges(null, changes)

                                AdditionUpdate(
                                    objectChange.key,
                                    versionedChange.version,
                                    insertionIndex,
                                    addedValues
                                )
                            } else {
                                ChangeUpdate(
                                    objectChange.key,
                                    versionedChange.version,
                                    insertionIndex,
                                    changes
                                )
                            }
                        }
                    }
                }
            }
        }

        iterator.close()
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(getRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys
        )
    )

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = getRequest.dataModel,
            updates = updates
        )
    )
}
