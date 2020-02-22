package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.rocksdb.use

internal typealias ScanChangesStoreAction<DM, P> = StoreAction<DM, P, ScanChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanChangesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)
    val columnFamilies = dataStore.getColumnFamilies(dbIndex)

    DBAccessor(dataStore.db).use { dbAccessor ->
        val columnToScan = if (scanRequest.toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnToScan)

        processScan(
            scanRequest,
            dataStore,
            dbAccessor,
            columnFamilies,
            dataStore.defaultReadOptions
        ) { key, creationVersion ->
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                dataStore.readValueWithCache(dbIndex, key, reference, version, valueReader)
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
                iterator,
                creationVersion,
                columnFamilies,
                key,
                scanRequest.select,
                scanRequest.fromVersion,
                scanRequest.toVersion,
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
