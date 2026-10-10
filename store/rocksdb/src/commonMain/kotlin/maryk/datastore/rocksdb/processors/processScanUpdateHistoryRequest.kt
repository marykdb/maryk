package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.IsUpdateHistoryRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.RocksDBReadContext
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.UpdateHistoryPaging
import maryk.datastore.shared.StoreAction

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, IsUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    cache: Cache,
    readContext: RocksDBReadContext,
) {
    val defaultReadOptions = readContext.defaultReadOptions
    val sequentialReadOptions = readContext.sequentialReadOptions
    val scanRequest = storeAction.request
    val paging = UpdateHistoryPaging(scanRequest)
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)
    val historicColumnFamilies = columnFamilies as? HistoricTableColumnFamilies
        ?: throw updateHistoryNotAvailable()

    if (!canUseUpdateHistoryIndex(dbIndex) || columnFamilies.updateHistory == null) {
        throw updateHistoryNotAvailable()
    }

    val expectedSize = scanRequest.limit.toInt().coerceAtLeast(4)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    val keySize = scanRequest.dataModel.Meta.keyByteSize

    DBAccessor(this).use { dbAccessor ->
        val historicalReader = scanRequest.toVersion?.let { toVersion ->
            HistoricalTableReader(dbAccessor, historicColumnFamilies, sequentialReadOptions, toVersion)
        }
        val softDeleteCache = RequestKeySoftDeleteCache(
            dbAccessor,
            historicColumnFamilies,
            defaultReadOptions,
            scanRequest.toVersion,
            historicalReader
        )
        historicalReader.use { reader ->
        dbAccessor.getIterator(sequentialReadOptions, historicColumnFamilies.historic.table).use { valuesIterator ->
        fun getSingleValues(
            key: Key<DM>,
            creationVersion: ULong,
            version: ULong,
            cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
        ): ValuesWithMetaData<DM>? =
            scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                iterator = valuesIterator,
                dataStore = this,
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
                    val deleted = if (version == scanRequest.toVersion) {
                        softDeleteCache.get(key.bytes, 0, key.size)
                    } else {
                        isSoftDeleted(
                            dbAccessor = dbAccessor,
                            columnFamilies = historicColumnFamilies,
                            readOptions = defaultReadOptions,
                            toVersion = version,
                            key = key.bytes,
                            keyLength = key.size,
                            historicalTableReader = null
                        )
                    }
                    if (values.isDeleted == deleted) values else values.copy(isDeleted = deleted)
                }
            }

        dbAccessor.getIterator(sequentialReadOptions, columnFamilies.updateHistory).use { historyIterator ->
        val continuation = paging.continuation
        val upperBound = paging.toVersion
        when {
            continuation != null -> historyIterator.seek(
                continuation.version.toReversedVersionBytes() + continuation.key.bytes + byteArrayOf(0)
            )
            upperBound != null -> historyIterator.seek(upperBound.toReversedVersionBytes())
            else -> historyIterator.seekToFirst()
        }

        while (historyIterator.isValid() && updates.size.toUInt() < paging.scanLimit) {
            val historyKey = historyIterator.key()
            if (historyKey.size != VERSION_BYTE_SIZE + keySize) {
                historyIterator.next()
                continue
            }
            val version = historyKey.readReversedVersionBytes(0)
            if (version < scanRequest.fromVersion) break

            var keyReadIndex = VERSION_BYTE_SIZE
            val key = scanRequest.dataModel.key {
                historyKey[keyReadIndex++]
            }
            if (!paging.accepts(version, key)) {
                historyIterator.next()
                continue
            }
            val isHardDelete = historyIterator.value().firstOrNull() == 1.toByte()
            // A tombstone belongs to this event, even if the key now identifies a new record.
            if (isHardDelete) {
                if (scanRequest.where == null) {
                    updates += RemovalUpdate(key = key, version = version, reason = HardDelete)
                }
                historyIterator.next()
                continue
            }
            val creationVersion = readCreationVersion(
                dbAccessor,
                historicColumnFamilies,
                defaultReadOptions,
                key.bytes,
                version
            )
            if (creationVersion == null) {
                historyIterator.next()
                continue
            }
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

            fun addCreation(creationEventVersion: ULong) {
                getSingleValues(key, creationVersion, creationEventVersion, cacheReader)?.let { valuesWithMeta ->
                    updates += AdditionUpdate(
                        key = key,
                        version = creationEventVersion,
                        firstVersion = valuesWithMeta.firstVersion,
                        insertionIndex = updates.size,
                        isDeleted = valuesWithMeta.isDeleted,
                        values = valuesWithMeta.values
                    )
                }
            }
            if (creationVersion == version) {
                addCreation(version)
                historyIterator.next()
                continue
            }

            val objectChange = scanRequest.dataModel.readTransactionIntoObjectChanges(
                iterator = valuesIterator,
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
            val updatedObjectChange = if (!scanRequest.filterSoftDeleted) {
                addSoftDeleteChangeIfMissing(
                    dbAccessor = dbAccessor,
                    columnFamilies = historicColumnFamilies,
                    readOptions = defaultReadOptions,
                    key = key,
                    fromVersion = version,
                    objectChange = objectChange
                )
            } else {
                objectChange
            }
            updatedObjectChange?.changes?.forEach { versionedChange ->
                val changes = versionedChange.changes
                if (changes.contains(ObjectCreate)) {
                    addCreation(versionedChange.version)
                } else {
                    updates += ChangeUpdate(
                        key = key,
                        version = versionedChange.version,
                        index = updates.size,
                        changes = changes
                    )
                }
            }

            historyIterator.next()
        }

        }
        }
        }
    }

    storeAction.response.complete(
        paging.response(updates)
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("Update history queries require keepAllVersions and a ready update history index")
