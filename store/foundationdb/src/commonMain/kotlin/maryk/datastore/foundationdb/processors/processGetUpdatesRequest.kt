package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.getLastVersion
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    cache: Cache
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL
    var insertionIndex = -1

    keyWalk@ for (key in getRequest.keys) {
        val result = this.tc.run {
            tr ->
            val existing = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).join()
            if (existing == null) {
                Pair<ULong?, DataObjectVersionedChange<DM>?>(null, null)
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
                    Pair<ULong?, DataObjectVersionedChange<DM>?>(null, null)
                } else {
                    // Determine last known version for ordered response metadata
                    val last = getLastVersion(tr, tableDirs, key)

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        runBlocking {
                            cache.readValue(dbIndex, key, reference, version, valueReader)
                        }
                    }

                    val objChanges = getRequest.dataModel.readTransactionIntoObjectChanges(
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

                    Pair(last, objChanges)
                }
            }
        }

        val (keyLastVersion, changes) = result
        if (keyLastVersion != null) {
            // Track ordered keys and response version regardless of initial changes
            lastResponseVersion = maxOf(lastResponseVersion, keyLastVersion)
            insertionIndex++
            matchingKeys.add(key)
        }

        changes?.let { oc ->
            updates += oc.changes.map { versionedChange ->
                val ch = versionedChange.changes
                if (ch.contains(ObjectCreate)) {
                    val addedValues = getRequest.dataModel.fromChanges(null, ch)
                    AdditionUpdate(
                        key = oc.key,
                        version = versionedChange.version,
                        firstVersion = versionedChange.version,
                        insertionIndex = insertionIndex,
                        isDeleted = false,
                        values = addedValues
                    )
                } else {
                    ChangeUpdate(
                        key = oc.key,
                        version = versionedChange.version,
                        index = insertionIndex,
                        changes = ch
                    )
                }
            }
        }
    }

    // Sort all updates on version ascending
    updates.sortBy { it.version }

    lastResponseVersion = minOf(getRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(
        0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys
        )
    )

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = getRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByKey,
        )
    )
}
