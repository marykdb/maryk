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
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion

internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootDataModel>

/** Processes a GetChangesRequest in a [storeAction] into a [RocksDBDataStore] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(getRequest.keys.size.coerceAtLeast(4))

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    DBAccessor(this).use { dbAccessor ->
        val dbIndex = getDataModelId(getRequest.dataModel)
        val columnFamilies = getColumnFamilies(dbIndex)

        val columnToScan = if ((getRequest.toVersion != null || getRequest.maxVersions > 1u) && columnFamilies is HistoricTableColumnFamilies) {
            columnFamilies.historic.table
        } else columnFamilies.table
        dbAccessor.getIterator(sequentialReadOptions, columnToScan).use { iterator ->
            val historicalReader = getRequest.toVersion?.let { toVersion ->
                (columnFamilies as? HistoricTableColumnFamilies)?.let {
                    HistoricalTableReader(dbAccessor, it, sequentialReadOptions, toVersion)
                }
            }
            historicalReader.use { reader ->
                val softDeleteCache = RequestKeySoftDeleteCache(
                    dbAccessor,
                    columnFamilies,
                    defaultReadOptions,
                    getRequest.toVersion,
                    reader
                )
                keyWalk@ for (key in getRequest.keys) {
                    readCreationVersion(
                        dbAccessor,
                        columnFamilies,
                        defaultReadOptions,
                        key.bytes,
                        getRequest.toVersion
                    )?.let { creationVersion ->
                            if (getRequest.shouldBeFiltered(
                                    dbAccessor,
                                    columnFamilies,
                                    defaultReadOptions,
                                    key.bytes,
                                    0,
                                    key.size,
                                    creationVersion,
                                    getRequest.toVersion,
                                    historicalReader = reader,
                                    softDeleteCache = softDeleteCache
                                )
                            ) {
                                continue@keyWalk
                            }

                            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                runBlocking {
                                    cache.readValue(dbIndex, key, reference, version, valueReader)
                                }
                            }

                            val objectChange = getRequest.dataModel.readTransactionIntoObjectChanges(
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
                            )
                            val updatedObjectChange = if (getRequest.needsSoftDeleteFallback() && columnFamilies is HistoricTableColumnFamilies) {
                                addSoftDeleteChangeIfMissing(
                                    dbAccessor = dbAccessor,
                                    columnFamilies = columnFamilies,
                                    key = key,
                                    readOptions = defaultReadOptions,
                                    fromVersion = getRequest.fromVersion,
                                    objectChange = objectChange
                                )
                            } else {
                                objectChange
                            }
                            updatedObjectChange?.also {
                                objectChanges.add(it)
                            }
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges,
            dataFetchType = FetchByKey,
        )
    )
}

private fun GetChangesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)
