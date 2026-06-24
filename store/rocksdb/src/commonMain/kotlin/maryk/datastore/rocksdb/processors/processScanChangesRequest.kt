package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    DBAccessor(this).use { dbAccessor ->
        val columnToScan = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val historicalReader = scanRequest.toVersion?.let { toVersion ->
            (columnFamilies as? HistoricTableColumnFamilies)?.let {
                HistoricalTableReader(dbAccessor, it, sequentialReadOptions, toVersion)
            }
        }
        val softDeleteCache = RequestKeySoftDeleteCache(
            dbAccessor,
            columnFamilies,
            defaultReadOptions,
            scanRequest.toVersion,
            historicalReader
        )
        dbAccessor.getIterator(sequentialReadOptions, columnToScan).use { iterator ->
            historicalReader.use { reader ->
            scanRequest.checkMaxVersions(keepAllVersions)

            val dataFetchType = processScan(
                scanRequest,
                dbAccessor,
                columnFamilies,
                defaultReadOptions,
                includeSortingKey = true,
                softDeleteCache = softDeleteCache,
                historicalReader = reader
            ) { key, creationVersion, sortingKey ->
                val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                    runBlocking {
                        cache.readValue(dbIndex, key, reference, version, valueReader)
                    }
                }

                val objectChange = scanRequest.dataModel.readTransactionIntoObjectChanges(
                    iterator,
                    creationVersion,
                    columnFamilies,
                    key,
                    scanRequest.select,
                    scanRequest.fromVersion,
                    scanRequest.toVersion,
                    scanRequest.maxVersions,
                    sortingKey,
                    cacheReader
                )
                val updatedObjectChange = if (scanRequest.needsSoftDeleteFallback() && columnFamilies is HistoricTableColumnFamilies) {
                    addSoftDeleteChangeIfMissing(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = defaultReadOptions,
                        key = key,
                        fromVersion = scanRequest.fromVersion,
                        objectChange = objectChange,
                        sortingKey = sortingKey
                    )
                } else {
                    objectChange
                }
                updatedObjectChange?.let {
                    objectChanges += it
                }
            }

            storeAction.response.complete(
                ChangesResponse(
                    dataModel = scanRequest.dataModel,
                    changes = objectChanges,
                    dataFetchType = dataFetchType,
                )
            )
            }
        }
    }
}

private fun ScanChangesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)
