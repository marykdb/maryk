package maryk.datastore.rocksdb.processors

import maryk.core.properties.IsRootModel
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
import maryk.rocksdb.use

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootModel>

/** Processes a ScanChangesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootModel> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    dataStore: RocksDBDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    DBAccessor(dataStore).use { dbAccessor ->
        val columnToScan = if ((scanRequest.toVersion != null || scanRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        scanRequest.checkMaxVersions(dataStore.keepAllVersions)

        processScan(
            scanRequest,
            dataStore,
            dbAccessor,
            columnFamilies,
            dataStore.defaultReadOptions
        ) { key, creationVersion, sortingKey ->
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
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
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges
        )
    )
}
