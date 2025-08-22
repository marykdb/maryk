package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
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

internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootDataModel>

/** Processes a GetChangesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    DBAccessor(this).use { dbAccessor ->
        val dbIndex = getDataModelId(getRequest.dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)

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
            changes = objectChanges,
            dataFetchType = FetchByKey,
        )
    )
}
