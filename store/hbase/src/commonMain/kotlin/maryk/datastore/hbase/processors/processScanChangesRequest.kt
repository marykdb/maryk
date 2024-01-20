package maryk.datastore.hbase.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)

    scanRequest.checkMaxVersions(dataStore.keepAllVersions)

    val table = dataStore.getTable(scanRequest.dataModel)

    processScan(
        table,
        scanRequest,
        dataStore,
    ) { key, createdVersion, result, sortingKey ->
        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        scanRequest.dataModel.readResultIntoObjectChanges(
            result = result,
            key = key,
            creationVersion = createdVersion,
            select = scanRequest.select,
            sortingKey = sortingKey,
            cachedRead = cacheReader
        )?.also {
            // Only add if not null
            objectChanges += it
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges
        )
    )
}
