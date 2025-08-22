package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.FetchByKey
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
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    DBAccessor(this).use { dbAccessor ->
        val dbIndex = this.getDataModelId(getRequest.dataModel)
        val columnFamilies = this.getColumnFamilies(dbIndex)

        val columnToScan = if ((getRequest.toVersion != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(defaultReadOptions, columnToScan)

        keyWalk@ for (key in getRequest.keys) {
            val mayExist = db.keyMayExist(columnFamilies.keys, key.bytes, null)
            if (mayExist) {
                val valueLength =
                    dbAccessor.get(columnFamilies.keys, defaultReadOptions, key.bytes, recyclableByteArray)

                if (valueLength != rocksDBNotFound) {
                    val creationVersion = recyclableByteArray.readVersionBytes()
                    if (getRequest.shouldBeFiltered(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            key.bytes,
                            0,
                            key.size,
                            creationVersion,
                            getRequest.toVersion
                        )
                    ) {
                        continue@keyWalk
                    }

                    val lastVersion = getLastVersion(dbAccessor, columnFamilies, defaultReadOptions, key)

                    insertionIndex++

                    matchingKeys.add(key)
                    lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        runBlocking {
                            cache.readValue(dbIndex, key, reference, version, valueReader)
                        }
                    }

                    getRequest.dataModel.readTransactionIntoObjectChanges(
                        iterator,
                        creationVersion,
                        columnFamilies,
                        key,
                        getRequest.select,
                        getRequest.fromVersion,
                        getRequest.toVersion,
                        getRequest.maxVersions,
                        null,
                        cacheReader
                    )?.also { objectChange ->
                        updates += objectChange.changes.map { versionedChange ->
                            val changes = versionedChange.changes

                            if (changes.contains(ObjectCreate)) {
                                val addedValues = getRequest.dataModel.fromChanges(null, changes)

                                AdditionUpdate(
                                    objectChange.key,
                                    versionedChange.version,
                                    versionedChange.version,
                                    insertionIndex,
                                    false,
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
            updates = updates,
            dataFetchType = FetchByKey,
        )
    )
}
