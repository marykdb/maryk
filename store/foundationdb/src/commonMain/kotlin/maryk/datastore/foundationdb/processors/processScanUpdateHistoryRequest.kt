package maryk.datastore.foundationdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.IsUpdateHistoryRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBReadContext
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.forEachInRangeBatch
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.UpdateHistoryPaging
import maryk.datastore.shared.StoreAction
import maryk.foundationdb.Transaction
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.foundationdb.Range as FDBRange

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, IsUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>
private val nextKeySuffix = byteArrayOf(0)

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
) {
    val scanRequest = storeAction.request
    val paging = UpdateHistoryPaging(scanRequest)
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex) as? HistoricTableDirectories
        ?: throw updateHistoryNotAvailable()

    if (!canUseUpdateHistoryIndex(dbIndex) || tableDirs.updateHistoryPrefix == null) {
        throw updateHistoryNotAvailable()
    }

    val expectedSize = scanRequest.limit.toInt().coerceAtLeast(4)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val historyPrefix = tableDirs.updateHistoryPrefix
    val historyStart = paging.continuation?.let {
        packKey(historyPrefix, it.version.toReversedVersionBytes(), it.key.bytes) + nextKeySuffix
    } ?: paging.toVersion?.let { packKey(historyPrefix, it.toReversedVersionBytes()) } ?: historyPrefix
    val historyEnd = if (scanRequest.fromVersion == 0uL) {
        historyPrefix.nextByteInSameLength()
    } else {
        packKey(historyPrefix, scanRequest.fromVersion.toReversedVersionBytes().nextByteInSameLength())
    }
    fun <T> runScanTransaction(block: (Transaction) -> T): T =
        runReadTransaction(readContext, dbIndex) { tr ->
            block(tr)
        }

    var nextStart = historyStart
    while (updates.size.toUInt() < paging.scanLimit) {
        val result = runScanTransaction { tr ->
            tr.forEachInRangeBatch(FDBRange(nextStart, historyEnd), false) { historyEntry ->
                if (updates.size.toUInt() >= paging.scanLimit) return@forEachInRangeBatch false
                if (historyEntry.key.size != historyPrefix.size + VERSION_BYTE_SIZE + keySize) return@forEachInRangeBatch true
                val version = historyEntry.key.readReversedVersionBytes(historyPrefix.size)
                if (version < scanRequest.fromVersion) return@forEachInRangeBatch false

                val keyOffset = historyPrefix.size + VERSION_BYTE_SIZE
                var keyReadIndex = keyOffset
                val key = scanRequest.dataModel.key {
                    historyEntry.key[keyReadIndex++]
                }
                if (!paging.accepts(version, key)) return@forEachInRangeBatch true
                val isHardDelete = historyEntry.value.firstOrNull() == 1.toByte()
                // Deletion is an event of the old record even after this key is reused.
                if (isHardDelete) {
                    if (scanRequest.where == null) {
                        updates += RemovalUpdate(key = key, version = version, reason = HardDelete)
                    }
                    return@forEachInRangeBatch updates.size.toUInt() < paging.scanLimit
                }
                val creationVersion = tr.readCreationVersion(tableDirs, key.bytes, version)
                    ?: return@forEachInRangeBatch true

                if (scanRequest.shouldBeFiltered(
                        transaction = tr,
                        tableDirs = tableDirs,
                        key = key.bytes,
                        keyOffset = 0,
                        keyLength = key.size,
                        createdVersion = creationVersion,
                        toVersion = version,
                        decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                    )
                ) return@forEachInRangeBatch true

                val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                    cache.readValue(dbIndex, key, reference, readVersion, valueReader)
                }

                fun addCreation(creationEventVersion: ULong) {
                    scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                        tr = tr,
                        creationVersion = creationVersion,
                        tableDirs = tableDirs,
                        key = key,
                        select = scanRequest.select,
                        toVersion = creationEventVersion,
                        cachedRead = cacheReader,
                        decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                    )?.let { valuesWithMeta ->
                        updates += AdditionUpdate(
                            key = key,
                            version = creationEventVersion,
                            firstVersion = valuesWithMeta.firstVersion,
                            insertionIndex = updates.size,
                            isDeleted = valuesWithMeta.isDeleted,
                            values = valuesWithMeta.values
                        )
                    }
                }
                if (creationVersion == version) {
                    addCreation(version)
                    return@forEachInRangeBatch updates.size.toUInt() < paging.scanLimit
                }

                val objectChange = scanRequest.dataModel.readTransactionIntoObjectChanges(
                    tr = tr,
                    creationVersion = creationVersion,
                    tableDirs = tableDirs,
                    key = key,
                    select = scanRequest.select,
                    fromVersion = version,
                    toVersion = version,
                    maxVersions = 1u,
                    sortingKey = null,
                    cachedRead = cacheReader,
                    decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                )
                val updatedObjectChange = if (!scanRequest.filterSoftDeleted) {
                    addSoftDeleteChangeIfMissing(
                        tr = tr,
                        tableDirs = tableDirs,
                        key = key,
                        fromVersion = version,
                        objectChange = objectChange,
                        toVersion = version,
                        maxVersions = 1u,
                    )
                } else {
                    objectChange
                }
                updatedObjectChange?.changes?.forEach { versionedChange ->
                    val changes = versionedChange.changes
                    if (changes.contains(ObjectCreate)) {
                        addCreation(versionedChange.version)
                    } else {
                        updates += ChangeUpdate(
                            key = key,
                            version = versionedChange.version,
                            index = updates.size,
                            changes = changes
                        )
                    }
                }
                updates.size.toUInt() < paging.scanLimit
            }
        }

        if (result.completed || result.stoppedByCallback || updates.size.toUInt() >= paging.scanLimit) break
        nextStart = result.lastKey?.let { it + nextKeySuffix } ?: break
    }

    storeAction.response.complete(
        paging.response(updates)
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("Update history queries require keepAllVersions and a ready update history index")
