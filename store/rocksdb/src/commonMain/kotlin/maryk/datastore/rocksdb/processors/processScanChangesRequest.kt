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
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val columnFamilies = getColumnFamilies(dbIndex)

    DBAccessor(this).use { dbAccessor ->
        val columnToScan = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(defaultReadOptions, columnToScan)

        scanRequest.checkMaxVersions(keepAllVersions)

        val dataFetchType = processScan(
            scanRequest,
            dbAccessor,
            columnFamilies,
            defaultReadOptions
        ) { key, creationVersion, sortingKey ->
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                runBlocking {
                    cache.readValue(dbIndex, key, reference, version, valueReader)
                }
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
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
            )?.let {
                // Only add if not null
                objectChanges += it
            }
        }

        iterator.close()

        storeAction.response.complete(
            ChangesResponse(
                dataModel = scanRequest.dataModel,
                changes = objectChanges,
                dataFetchType = dataFetchType,
            )
        )
    }
}
