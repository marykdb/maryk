package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
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
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import maryk.foundationdb.Transaction

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    cache: Cache,
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(keepAllVersions)
    getRequest.checkMaxVersions(keepAllVersions)

    val dbIndex = getDataModelId(getRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val expectedSize = getRequest.keys.size.coerceAtLeast(4)
    val matchingKeys = ArrayList<Key<DM>>(expectedSize)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)

    var lastResponseVersion = 0uL
    var insertionIndex = -1

    this.runTransaction { tr ->
        keyWalk@ for (key in getRequest.keys) {
            var getSingleValues: ((ULong?) -> ValuesWithMetaData<DM>?)? = null
            val result = run {
                val creationVersion = tr.readCreationVersion(tableDirs, key.bytes, getRequest.toVersion)
                if (creationVersion == null) {
                    Pair<ULong?, DataObjectVersionedChange<DM>?>(null, null)
                } else {
                    if (getRequest.shouldBeFiltered(
                            transaction = tr,
                            tableDirs = tableDirs,
                            key = key.bytes,
                            keyOffset = 0,
                            keyLength = key.size,
                            createdVersion = creationVersion,
                            toVersion = getRequest.toVersion,
                            decryptValue = this@processGetUpdatesRequest::decryptValueIfNeeded
                        )
                    ) {
                        Pair<ULong?, DataObjectVersionedChange<DM>?>(null, null)
                    } else {
                        // Determine last known version for ordered response metadata
                        val last = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult()
                            ?.readHLCTimestampIfExact()
                            ?: getRequest.toVersion
                            ?: return@run Pair<ULong?, DataObjectVersionedChange<DM>?>(null, null)

                        val cacheReader =
                            { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                                cache.readValue(dbIndex, key, reference, version, valueReader)
                            }

                        getSingleValues = { version ->
                            val readVersion = version.takeIf { tableDirs is HistoricTableDirectories }
                            getRequest.dataModel.readTransactionIntoValuesWithMetaData(
                                tr = tr,
                                creationVersion = creationVersion,
                                tableDirs = tableDirs,
                                key = key,
                                select = getRequest.select,
                                toVersion = readVersion,
                                cachedRead = cacheReader,
                                decryptValue = this@processGetUpdatesRequest::decryptValueIfNeeded
                            )?.withSoftDeleteState(tr, tableDirs, key.bytes, readVersion)
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
                            cachedRead = cacheReader,
                            decryptValue = this@processGetUpdatesRequest::decryptValueIfNeeded
                        )

                        val fallbackChanges = if (getRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                            addSoftDeleteChangeIfMissing(
                                tr = tr,
                                tableDirs = tableDirs,
                                key = key,
                                fromVersion = getRequest.fromVersion,
                                objectChange = objChanges
                            )
                        } else {
                            objChanges
                        }

                        Pair(last, fallbackChanges)
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
                for (versionedChange in oc.changes) {
                    val ch = versionedChange.changes
                    if (ch.contains(ObjectCreate)) {
                        getSingleValues?.invoke(versionedChange.version)?.let { valuesWithMeta ->
                            updates += AdditionUpdate(
                                key = oc.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = insertionIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    } else {
                        updates += ChangeUpdate(
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
}

private fun GetUpdatesRequest<*>.needsSoftDeleteFallback() =
    toVersion == null && (maxVersions > 1u || !filterSoftDeleted)

private fun <DM : IsRootDataModel> ValuesWithMetaData<DM>.withSoftDeleteState(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    toVersion: ULong?
): ValuesWithMetaData<DM> {
    if (toVersion == null) {
        return this
    }

    val deleted = isSoftDeleted(tr, tableDirs, toVersion, key)
    return if (isDeleted == deleted) this else copy(isDeleted = deleted)
}
