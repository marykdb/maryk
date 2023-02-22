package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias GetChangesStoreAction<DM, P> = StoreAction<DM, P, GetChangesRequest<DM, P>, ChangesResponse<DM, P>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions>

/** Processes a GetChangesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    cache: Cache
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)

    DBAccessor(dataStore).use { dbAccessor ->
        val dbIndex = dataStore.getDataModelId(getRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        val columnToScan = if ((getRequest.toVersion != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
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

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        cache.readValue(dbIndex, key, reference, version, valueReader)
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
                    )?.also {
                        // Only add if not null
                        objectChanges.add(it)
                    }
                }
            }
        }

        iterator.close()
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges
        )
    )
}
