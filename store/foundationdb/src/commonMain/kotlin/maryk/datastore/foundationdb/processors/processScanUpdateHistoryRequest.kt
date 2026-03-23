package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.foundationdb.ReadTransaction
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.foundationdb.Range as FDBRange

internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, ScanUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdateHistoryStoreAction = ScanUpdateHistoryStoreAction<IsRootDataModel>

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanUpdateHistoryRequest(
    storeAction: ScanUpdateHistoryStoreAction<DM>,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex) as? HistoricTableDirectories
        ?: throw updateHistoryNotAvailable()

    if (!canUseUpdateHistoryIndex(dbIndex) || tableDirs.updateHistoryPrefix == null) {
        throw updateHistoryNotAvailable()
    }

    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val keySize = scanRequest.dataModel.Meta.keyByteSize

    runTransaction { tr ->
        val historyPrefix = tableDirs.updateHistoryPrefix
        val historyStart = scanRequest.toVersion?.let { packKey(historyPrefix, it.toReversedVersionBytes()) } ?: historyPrefix
        val historyEnd = if (scanRequest.fromVersion == 0uL) {
            historyPrefix.nextByteInSameLength()
        } else {
            packKey(historyPrefix, scanRequest.fromVersion.toReversedVersionBytes().nextByteInSameLength())
        }
        val historyIterator = tr.getRange(FDBRange(historyStart, historyEnd), ReadTransaction.ROW_LIMIT_UNLIMITED, false).iterator()

        while (historyIterator.hasNext() && updates.size.toUInt() < scanRequest.limit) {
            val historyEntry = historyIterator.nextBlocking()
            val version = historyEntry.key.readReversedVersionBytes(historyPrefix.size)
            if (version < scanRequest.fromVersion) break

            val keyOffset = historyPrefix.size + maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
            val key = scanRequest.dataModel.key(historyEntry.key.copyOfRange(keyOffset, keyOffset + keySize))
            val isHardDelete = historyEntry.value.firstOrNull() == 1.toByte()
            val createdBytes = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
            if (createdBytes == null) {
                if (isHardDelete && scanRequest.where == null) {
                    updates += RemovalUpdate(
                        key = key,
                        version = version,
                        reason = HardDelete
                    )
                }
                continue
            }

            val creationVersion = HLC.fromStorageBytes(createdBytes).timestamp
            if (scanRequest.shouldBeFiltered(
                    transaction = tr,
                    tableDirs = tableDirs,
                    key = key.bytes,
                    keyOffset = 0,
                    keyLength = key.size,
                    createdVersion = creationVersion,
                    toVersion = version,
                    decryptValue = this@processScanUpdateHistoryRequest::decryptValueIfNeeded
                )
            ) continue

            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, readVersion: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, readVersion, valueReader)
            }

            scanRequest.dataModel.readTransactionIntoObjectChanges(
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
                decryptValue = this@processScanUpdateHistoryRequest::decryptValueIfNeeded
            )?.let { changes ->
                if (!scanRequest.filterSoftDeleted) {
                    addSoftDeleteChangeIfMissing(
                        tr = tr,
                        tableDirs = tableDirs,
                        key = key,
                        fromVersion = version,
                        objectChange = changes
                    )
                } else {
                    changes
                }
            }?.changes?.mapNotNull { versionedChange ->
                val changes = versionedChange.changes
                if (changes.contains(ObjectCreate)) {
                    scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                        tr = tr,
                        creationVersion = creationVersion,
                        tableDirs = tableDirs,
                        key = key,
                        select = scanRequest.select,
                        toVersion = versionedChange.version,
                        cachedRead = cacheReader,
                        decryptValue = this@processScanUpdateHistoryRequest::decryptValueIfNeeded
                    )?.let { valuesWithMeta ->
                        AdditionUpdate(
                            key = key,
                            version = versionedChange.version,
                            firstVersion = valuesWithMeta.firstVersion,
                            insertionIndex = updates.size,
                            isDeleted = valuesWithMeta.isDeleted,
                            values = valuesWithMeta.values
                        )
                    }
                } else {
                    ChangeUpdate(
                        key = key,
                        version = versionedChange.version,
                        index = updates.size,
                        changes = changes
                    )
                }
            }?.let { updates += it }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    )
}

private fun updateHistoryNotAvailable() =
    RequestException("scanUpdateHistory requires keepAllVersions and a ready update history index")
