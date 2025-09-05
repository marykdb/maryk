package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.getLastVersion
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    var insertionIndex = -1

    scanRequest.checkMaxVersions(keepAllVersions)

    fun getSingleValues(key: Key<DM>, creationVersion: ULong, cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?): ValuesWithMetaData<DM>? {
        return this.tc.run { tr ->
            scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                toVersion = scanRequest.toVersion,
                cachedRead = cacheReader
            )
        }
    }

    val dataFetchType = this.processScan(
        scanRequest = scanRequest,
        tableDirs = tableDirs,
        scanSetup = {
            (it as? IndexScan)?.let { indexScan ->
                sortingKeys = mutableListOf()
                sortingIndex = indexScan.index
            }
        }
    ) { key, creationVersion, sortingKey ->
        insertionIndex++

        matchingKeys.add(key)

        // Add sorting index if requested
        sortingIndex?.let { idx ->
            this.tc.run { tr ->
                val getter = StoreValuesGetter(tr, tableDirs)
                getter.moveToKey(key.bytes, scanRequest.toVersion)
                idx.toStorageByteArrayForIndex(getter, key.bytes)?.let { indexableBytes ->
                    sortingKeys?.add(indexableBytes)
                }
            }
        }

        // Determine last known version for ordered response metadata
        this.tc.run { tr ->
            val last = getLastVersion(tr, tableDirs, key)
            lastResponseVersion = maxOf(lastResponseVersion, last)
        }

        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            runBlocking {
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }
        }

        val objectChange = this.tc.run { tr ->
            scanRequest.dataModel.readTransactionIntoObjectChanges(
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
        }

        objectChange?.let { oc ->
            for (versionedChange in oc.changes) {
                val changes = versionedChange.changes
                val update = if (changes.find { it is ObjectCreate } != null) {
                    val addedValues = scanRequest.dataModel.fromChanges(null, changes)
                    AdditionUpdate(
                        key = oc.key,
                        version = versionedChange.version,
                        firstVersion = versionedChange.version,
                        insertionIndex = insertionIndex,
                        isDeleted = false,
                        values = addedValues
                    )
                } else {
                    if (scanRequest.orderedKeys?.contains(oc.key) != false) {
                        ChangeUpdate(
                            key = oc.key,
                            version = versionedChange.version,
                            index = insertionIndex,
                            changes = changes
                        )
                    } else {
                        getSingleValues(key, creationVersion, cacheReader)?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = oc.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = insertionIndex,
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    }
                }
                update?.also { updates += it }
            }
        }
    }

    // Sort updates by version ascending
    updates.sortBy { it.version }

    lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(
        0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys,
            sortingKeys = sortingKeys?.map { Bytes(it) }
        )
    )

    // orderedKeys reconciliation
    scanRequest.orderedKeys?.let { orderedKeys ->
        // Removals for keys no longer in range
        orderedKeys.subtract(matchingKeys.toSet()).let { removedKeys ->
            this.tc.run { tr ->
                for (removedKey in removedKeys) {
                    val exists = tr.get(packKey(tableDirs.keysPrefix, removedKey.bytes)).join()
                    updates += RemovalUpdate(
                        key = removedKey,
                        version = lastResponseVersion,
                        reason = when {
                            exists == null -> HardDelete
                            isSoftDeleted(tr, tableDirs, scanRequest.toVersion, removedKey.bytes) -> SoftDelete
                            else -> NotInRange
                        }
                    )
                }
            }
        }

        // Additions for keys newly added in range relative to old orderedKeys
        matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
            this.tc.run { tr ->
                for (addedKey in addedKeys) {
                    val createdBytes = tr.get(packKey(tableDirs.keysPrefix, addedKey.bytes)).join()
                    if (createdBytes != null) {
                        val createdVersion = HLC.fromStorageBytes(createdBytes).timestamp

                        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            runBlocking {
                                cache.readValue(dbIndex, addedKey, reference, version, valueReader)
                            }
                        }

                        getSingleValues(addedKey, createdVersion, cacheReader)?.let { valuesWithMeta ->
                            updates += AdditionUpdate(
                                key = addedKey,
                                version = lastResponseVersion,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = matchingKeys.indexOf(addedKey),
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates,
            dataFetchType = dataFetchType,
        )
    )
}

/** Simple getter to compute index values for current key within a single transaction */
private class StoreValuesGetter(
    private val tr: com.apple.foundationdb.Transaction,
    private val tableDirs: IsTableDirectories
) : IsValuesGetter {
    private lateinit var keyBytes: ByteArray

    fun moveToKey(keyBytes: ByteArray, toVersion: ULong?) {
        this.keyBytes = keyBytes
        this.toVersion = toVersion
    }

    private var toVersion: ULong? = null

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>
    ): T? {
        val keyAndRef = combineToByteArray(keyBytes, propertyReference.toStorageByteArray())
        return tr.getValue(tableDirs, toVersion, keyAndRef, keyBytes.size) { valueBytes, offset, length ->
            valueBytes.convertToValue(propertyReference, offset, length) as T?
        }
    }
}
