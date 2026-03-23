package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, ScanUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)
    val historicColumnFamilies = columnFamilies as? HistoricTableColumnFamilies
        ?: throw updateHistoryNotAvailable()

    if (!canUseUpdateHistoryIndex(dbIndex) || columnFamilies.updateHistory == null) {
        throw updateHistoryNotAvailable()
    }

    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val keySize = scanRequest.dataModel.Meta.keyByteSize

    DBAccessor(this).use { dbAccessor ->
        fun getSingleValues(
            key: Key<DM>,
            creationVersion: ULong,
            version: ULong,
            cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
        ): ValuesWithMetaData<DM>? =
            dbAccessor.getIterator(defaultReadOptions, historicColumnFamilies.historic.table).use { iterator ->
                scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                    iterator = iterator,
                    creationVersion = creationVersion,
                    columnFamilies = historicColumnFamilies,
                    key = key,
                    select = scanRequest.select,
                    toVersion = version,
                    cachedRead = cacheReader
                )?.let { values ->
                    if (scanRequest.filterSoftDeleted) {
                        values
                    } else {
                        val deleted = isSoftDeleted(
                            dbAccessor,
                            historicColumnFamilies,
                            defaultReadOptions,
                            version,
                            key.bytes,
                            0,
                            key.size
                        )
                        if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                    }
                }
            }

        val historyIterator = dbAccessor.getIterator(defaultReadOptions, columnFamilies.updateHistory)
        when (val toVersion = scanRequest.toVersion) {
            null -> historyIterator.seekToFirst()
            else -> historyIterator.seek(toVersion.toReversedVersionBytes())
        }

        while (historyIterator.isValid() && updates.size.toUInt() < scanRequest.limit) {
            val historyKey = historyIterator.key()
            val version = historyKey.readReversedVersionBytes(0)
            if (version < scanRequest.fromVersion) break

            val key = Key<DM>(historyKey.copyOfRange(VERSION_BYTE_SIZE, VERSION_BYTE_SIZE + keySize))
            val isHardDelete = historyIterator.value().firstOrNull() == 1.toByte()
            val createdVersionLength = dbAccessor.get(columnFamilies.keys, defaultReadOptions, key.bytes, recyclableByteArray)
            if (createdVersionLength == rocksDBNotFound) {
                if (isHardDelete && scanRequest.where == null) {
                    updates += RemovalUpdate(
                        key = key,
                        version = version,
                        reason = HardDelete
                    )
                }
                historyIterator.next()
                continue
            }

            val creationVersion = recyclableByteArray.readVersionBytes()
            if (scanRequest.shouldBeFiltered(
                    dbAccessor,
                    historicColumnFamilies,
                    defaultReadOptions,
                    key.bytes,
                    0,
                    key.size,
                    creationVersion,
                    version
                )
            ) {
                historyIterator.next()
                continue
            }

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                runBlocking {
                    cache.readValue(dbIndex, key, reference, readVersion, valueReader)
                }
            }

            dbAccessor.getIterator(defaultReadOptions, historicColumnFamilies.historic.table).use { objectIterator ->
                scanRequest.dataModel.readTransactionIntoObjectChanges(
                    iterator = objectIterator,
                    creationVersion = creationVersion,
                    columnFamilies = historicColumnFamilies,
                    key = key,
                    select = scanRequest.select,
                    fromVersion = version,
                    toVersion = version,
                    maxVersions = 1u,
                    sortingKey = null,
                    cachedRead = cacheReader
                )
            }?.let { changes ->
                if (!scanRequest.filterSoftDeleted) {
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = historicColumnFamilies,
                        readOptions = defaultReadOptions,
                        key = key,
                        fromVersion = version,
                        objectChange = changes
                    )
                } else {
                    changes
                }
            }?.changes?.mapNotNull { versionedChange ->
                val changes = versionedChange.changes
                if (changes.contains(ObjectCreate)) {
                    getSingleValues(key, creationVersion, versionedChange.version, cacheReader)?.let { valuesWithMeta ->
                        AdditionUpdate(
                            key = key,
                            version = versionedChange.version,
                            firstVersion = valuesWithMeta.firstVersion,
                            insertionIndex = updates.size,
                            isDeleted = valuesWithMeta.isDeleted,
                            values = valuesWithMeta.values
                        )
                    }
                } else {
                    ChangeUpdate(
                        key = key,
                        version = versionedChange.version,
                        index = updates.size,
                        changes = changes
                    )
                }
            }?.let { updates += it }

            historyIterator.next()
        }

        historyIterator.close()
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("scanUpdateHistory requires keepAllVersions and a ready update history index")
