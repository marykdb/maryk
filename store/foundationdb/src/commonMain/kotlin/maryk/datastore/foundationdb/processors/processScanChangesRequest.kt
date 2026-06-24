package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    cache: Cache,
) {
    val scanRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    scanRequest.checkMaxVersions(keepAllVersions)

    val dataFetchType = this.processScan(
        scanRequest = scanRequest,
        tableDirs = tableDirs,
        scanSetup = { /* no-op */ },
    ) { tr, key, creationVersion, sortingKey ->
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }

            val change = scanRequest.dataModel.readTransactionIntoObjectChanges(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                fromVersion = scanRequest.fromVersion,
                toVersion = scanRequest.toVersion,
                maxVersions = scanRequest.maxVersions,
                sortingKey = sortingKey,
                cachedRead = cacheReader
            )
            val updated = if (scanRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                addSoftDeleteChangeIfMissing(
                    tr = tr,
                    tableDirs = tableDirs,
                    key = key,
                    fromVersion = scanRequest.fromVersion,
                    objectChange = change,
                    sortingKey = sortingKey
                )
            } else {
                change
            }
            updated?.let {
                objectChanges += updated
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

private fun ScanChangesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)
