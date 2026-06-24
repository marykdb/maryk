package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByKey
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
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
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(getRequest.keys.size.coerceAtLeast(4))

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    runTransaction { tr ->
        keyWalk@ for (key in getRequest.keys) {
            val changes: DataObjectVersionedChange<DM>? = run {
                val creationVersion = tr.readCreationVersion(tableDirs, key.bytes, getRequest.toVersion)
                if (creationVersion == null) {
                    null
                } else {
                    if (getRequest.shouldBeFiltered(
                            transaction = tr,
                            tableDirs = tableDirs,
                            key = key.bytes,
                            keyOffset = 0,
                            keyLength = key.size,
                            createdVersion = creationVersion,
                            toVersion = getRequest.toVersion,
                            decryptValue = this@processGetChangesRequest::decryptValueIfNeeded
                        )
                    ) {
                        null
                    } else {
                        val cacheReader =
                            { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                cache.readValue(dbIndex, key, reference, version, valueReader)
                            }

                        val objectChange = getRequest.dataModel.readTransactionIntoObjectChanges(
                            tr = tr,
                            creationVersion = creationVersion,
                            tableDirs = tableDirs,
                            key = key,
                            select = getRequest.select,
                            fromVersion = getRequest.fromVersion,
                            toVersion = getRequest.toVersion,
                            maxVersions = getRequest.maxVersions,
                            sortingKey = null,
                            cachedRead = cacheReader,
                            decryptValue = this@processGetChangesRequest::decryptValueIfNeeded
                        )

                        if (getRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                            addSoftDeleteChangeIfMissing(
                                tr = tr,
                                tableDirs = tableDirs,
                                key = key,
                                fromVersion = getRequest.fromVersion,
                                objectChange = objectChange
                            )
                        } else {
                            objectChange
                        }
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

private fun GetChangesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)
