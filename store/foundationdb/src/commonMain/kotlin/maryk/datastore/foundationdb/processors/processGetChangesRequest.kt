package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion

internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootDataModel>

/** Processes a GetChangesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
    cache: Cache,
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    runTransaction { tr ->
        keyWalk@ for (key in getRequest.keys) {
            val changes: DataObjectVersionedChange<DM>? = run {
                val existing = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
                if (existing == null) {
                    null
                } else {
                    val creationVersion = HLC.fromStorageBytes(existing, 0).timestamp

                    if (getRequest.shouldBeFiltered(
                            transaction = tr,
                            tableDirs = tableDirs,
                            key = key.bytes,
                            keyOffset = 0,
                            keyLength = key.size,
                            createdVersion = creationVersion,
                            toVersion = getRequest.toVersion
                        )
                    ) {
                        null
                    } else {
                        val cacheReader =
                            { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                cache.readValue(dbIndex, key, reference, version, valueReader)
                            }

                        getRequest.dataModel.readTransactionIntoObjectChanges(
                            tr = tr,
                            creationVersion = creationVersion,
                            tableDirs = tableDirs,
                            key = key,
                            select = getRequest.select,
                            fromVersion = getRequest.fromVersion,
                            toVersion = getRequest.toVersion,
                            maxVersions = getRequest.maxVersions,
                            sortingKey = null,
                            cachedRead = cacheReader
                        )
                    }
                }
            }

            if (changes == null) continue@keyWalk
            objectChanges += changes
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
