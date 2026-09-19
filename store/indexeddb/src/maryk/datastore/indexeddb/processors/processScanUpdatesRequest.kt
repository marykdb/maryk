package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.scanInBatches
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.orderToScanType
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

internal var hardDeleteHistoryRowReadObserver: ((ByteArray) -> Unit)? = null

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)
    if (!keepAllVersions && request.maxVersions > 1u) {
        throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
    }

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val indexStoreName = "i:$modelId"
    val historicIndexStoreName = "hi:$modelId"
    val changeStoreName = "c:$modelId"
    val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
    val indexKeyScanRange = if (request.startKey == null) {
        keyScanRange
    } else {
        request.dataModel.createScanRange(request.where, null, request.includeStart)
    }
    val scanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)

    val includeHardDeletes = keepUpdateHistoryIndex &&
        request.fromVersion > 0uL &&
        request.where == null &&
        scanType !is IndexScan
    val usesUpdateHistoryIndex = request.canUseUpdateHistoryIndex() && keepUpdateHistoryIndex && !includeHardDeletes
    val scanRows = when {
        usesUpdateHistoryIndex -> collectUpdateHistoryScanUpdateRows(
            request = request,
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            historicTableStoreName = historicTableStoreName,
            keyScanRange = keyScanRange,
            limit = request.limit,
        )
        scanType is IndexScan -> collectIndexScanUpdateRows(
            request = request,
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            indexStoreName = indexStoreName,
            historicTableStoreName = historicTableStoreName,
            historicIndexStoreName = historicIndexStoreName,
            keyScanRange = indexKeyScanRange,
            indexScan = scanType,
            limit = request.limit,
        )
        else -> collectTableScanUpdateRows(
            request = request,
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            historicTableStoreName = historicTableStoreName,
            keyScanRange = keyScanRange,
            tableScan = scanType as? TableScan ?: TableScan(ASC),
            limit = request.limit,
        )
    }
    val hardDeletes = if (includeHardDeletes) {
        collectHardDeleteScanUpdates(
            request = request,
            modelId = modelId,
            keyScanRange = keyScanRange,
            direction = (scanType as? TableScan)?.direction ?: ASC,
        )
    } else {
        emptyList()
    }
    val selectedScanRows = selectScanUpdateCandidates(scanRows, hardDeletes, request.limit)

    val rows = selectedScanRows.rows
    val highestVersion = minOf(
        request.toVersion ?: ULong.MAX_VALUE,
        maxOf(
            rows.maxOfOrNull { it.lastVersion } ?: 0uL,
            selectedScanRows.hardDeletes.maxOfOrNull { it.version } ?: 0uL,
        ),
    )
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val matchingKeys = rows.map { it.key }
    val matchingKeyIndices = matchingKeys.withIndex().associate { it.value to it.index }
    val orderedKeysUpdate = OrderedKeysUpdate(matchingKeys, highestVersion, selectedScanRows.sortingKeys)
    rows.forEachIndexed { index, record ->
        val versionedChanges = byteStore.readChangeLog(
            dataModel = request.dataModel,
            changeStoreName = changeStoreName,
            historicTableStoreName = historicTableStoreName.takeIf { keepAllVersions },
            currentRecord = record.takeUnless { keepAllVersions },
            keyBytes = record.key.bytes,
            fromVersion = request.fromVersion,
            toVersion = request.toVersion,
            maxVersions = request.maxVersions,
            select = request.select,
            decryptValue = { qualifier, value -> sensitiveFields.decryptValueIfNeeded(modelId, record.key.bytes, qualifier, value) },
            decryptChangePayload = { version, value ->
                sensitiveFields.decryptChangeLogPayloadIfNeeded(modelId, record.key.bytes, version, value)
            },
        )
        for (versionedChange in versionedChanges) {
            if (versionedChange.changes.any { it is ObjectCreate } || request.orderedKeysSet?.contains(record.key) == false) {
                updates += AdditionUpdate(
                    key = record.key,
                    version = versionedChange.version,
                    firstVersion = record.firstVersion,
                    insertionIndex = index,
                    isDeleted = record.isDeleted,
                    values = record.values,
                )
            } else {
                updates += ChangeUpdate(
                    key = record.key,
                    version = versionedChange.version,
                    index = index,
                    changes = versionedChange.changes,
                )
            }
        }
    }
    updates += selectedScanRows.hardDeletes
    request.orderedKeys?.let { orderedKeys ->
        val matchingSet = matchingKeys.toSet()
        val orderedSet = request.orderedKeysSet ?: return@let
        val hardDeletedKeys = selectedScanRows.hardDeletes.mapTo(HashSet()) { it.key }
        for (removedKey in orderedKeys.filter { it !in matchingSet }) {
            if (removedKey in hardDeletedKeys) continue
            val meta = byteStore.get(keyStoreName, removedKey.bytes)?.let(::decodeRecordMeta)
            val hardDeleteVersion = if (keepUpdateHistoryIndex) {
                findHardDeleteVersion(modelId, removedKey.bytes, request)
            } else {
                null
            }
            updates += RemovalUpdate(
                key = removedKey,
                version = hardDeleteVersion ?: highestVersion,
                reason = when {
                    hardDeleteVersion != null || meta == null -> HardDelete
                    meta.isDeleted -> SoftDelete
                    else -> NotInRange
                },
            )
        }
        for (addedRecord in rows.filter { it.key !in orderedSet }) {
            if (updates.none { it is AdditionUpdate<*> && it.key == addedRecord.key }) {
                updates += AdditionUpdate(
                    key = addedRecord.key,
                    version = highestVersion,
                    firstVersion = addedRecord.firstVersion,
                    insertionIndex = matchingKeyIndices.getValue(addedRecord.key),
                    isDeleted = addedRecord.isDeleted,
                    values = addedRecord.values,
                )
            }
        }
    }

    updates.sortBy { it.version }
    storeAction.response.complete(
        UpdatesResponse(
            dataModel = request.dataModel,
            updates = listOf(orderedKeysUpdate) + updates,
            dataFetchType = scanRows.dataFetchType,
        )
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectTableScanUpdateRows(
    request: ScanUpdatesRequest<DM>,
    keyStoreName: String,
    tableStoreName: String,
    historicTableStoreName: String,
    keyScanRange: KeyScanRanges,
    tableScan: TableScan,
    limit: UInt = request.limit,
): ScanUpdateRows<DM> {
    val direction = tableScan.direction
    val rows = mutableListOf<ValuesWithMetaData<DM>>()
    val ranges = if (direction == ASC) keyScanRange.ranges else keyScanRange.ranges.asReversed()

    rangeLoop@ for (range in ranges) {
        byteStore.scanInBatches(
            storeName = keyStoreName,
            startKey = if (direction == ASC) range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart) else range.start.takeUnless { it.isEmpty() },
            endKey = if (direction == ASC) range.end else range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart),
            includeEnd = direction == DESC || range.endInclusive,
            reverse = direction == DESC,
            targetLimit = UInt.MAX_VALUE,
        ) { keyBytes, snapshotBytes ->
            if (direction == ASC && range.keyOutOfRange(keyBytes)) return@scanInBatches false
            if (direction == DESC && range.keyBeforeStart(keyBytes)) return@scanInBatches false
            if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

            val toVersion = request.toVersion
            val record = if (toVersion == null) {
                decodeCurrentSnapshotRecord(
                    request.dataModel,
                    keyBytes,
                    snapshotBytes,
                    request.select,
                    { qualifier, value -> sensitiveFields.decryptValueIfNeeded(getDataModelId(request.dataModel), keyBytes, qualifier, value) },
                )
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            } else {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            }
                ?: return@scanInBatches true
            if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

            rows += record
            rows.size.toUInt() < limit
        }
        if (rows.size.toUInt() == limit) break@rangeLoop
    }

    return ScanUpdateRows(
        rows = rows,
        sortingKeys = null,
        dataFetchType = FetchByTableScan(direction, request.startKey?.bytes, null),
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectIndexScanUpdateRows(
    request: ScanUpdatesRequest<DM>,
    keyStoreName: String,
    tableStoreName: String,
    indexStoreName: String,
    historicTableStoreName: String,
    historicIndexStoreName: String,
    keyScanRange: KeyScanRanges,
    indexScan: IndexScan,
    limit: UInt = request.limit,
): ScanUpdateRows<DM> {
    val rows = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))
    val sortingKeys = ArrayList<Bytes>(request.limit.toInt().coerceAtLeast(4))
    val seenKeys = mutableSetOf<String>()
    val keySize = request.dataModel.Meta.keyByteSize
    val indexPrefix = createIndexKeyPrefix(indexScan.index.referenceStorageByteArray.bytes)
    val indexKeyScanRange = if (request.startKey == null) {
        keyScanRange
    } else {
        request.dataModel.createScanRange(request.where, null, request.includeStart)
    }
    val baseIndexRanges = indexScan.index.createScanRange(request.where, indexKeyScanRange)
    val startIndexValue = request.startKey?.let { startKey ->
        val toVersion = request.toVersion
        val record = if (toVersion != null) {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, startKey.bytes, toVersion, null)
        } else {
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, startKey.bytes, null)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, startKey.bytes, null)
        }
        record?.let {
            val allIndexValues = indexScan.index.toStorageByteArraysForIndex(it.values, startKey.bytes)
            val matchedIndexValues = allIndexValues.filter { indexValue ->
                resolveIndexValueSize(indexValue, keySize, indexScan.index.indexPartCount)?.let { valueSize ->
                    baseIndexRanges.matchesPartials(indexValue, length = valueSize, sourceEnd = indexValue.size) &&
                        baseIndexRanges.ranges.any { range ->
                            val rangeLength = indexRangeLength(baseIndexRanges, range, valueSize)
                            !range.keyBeforeStart(indexValue, length = rangeLength) &&
                                !range.keyOutOfRange(indexValue, length = rangeLength)
                        }
                } == true
            }
            when (indexScan.direction) {
                ASC -> matchedIndexValues.minWithOrNull { a, b -> a compareTo b }
                DESC -> matchedIndexValues.maxWithOrNull { a, b -> a compareTo b }
            }
        }
    }
    val indexRanges = baseIndexRanges
    val overallStartKey = when (indexScan.direction) {
        ASC -> startIndexValue?.let {
            indexRanges.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
        } ?: indexRanges.ranges.first().start
        DESC -> indexRanges.ranges.first().getDescendingStartKey(startIndexValue, keyScanRange.includeStart)
    }
    val overallStopKey = when (indexScan.direction) {
        ASC -> indexRanges.ranges.last().getDescendingStartKey()
        DESC -> indexRanges.ranges.last().getAscendingStartKey()
    }

    val rangeList = if (indexScan.direction == ASC) indexRanges.ranges else indexRanges.ranges.asReversed()
    rangeLoop@ for (range in rangeList) {
        val startKey = when (indexScan.direction) {
            ASC -> createIndexKeyWithPrefix(
                indexPrefix,
                startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
            )
            DESC -> indexPrefix
        }
        val endKey = when (indexScan.direction) {
            ASC -> when (val rangeEnd = range.getDescendingStartKey()) {
                null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                else -> if (rangeEnd.isEmpty()) {
                    createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                } else {
                    createIndexKeyWithPrefix(indexPrefix, rangeEnd)
                }
            }
            DESC -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
        }
        val historicRows = request.toVersion?.let { toVersion ->
            byteStore.readHistoricIndexRows(
                storeName = historicIndexStoreName,
                startKey = startKey,
                endKey = endKey,
                includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                toVersion = toVersion,
                reverse = indexScan.direction == DESC,
            )
        }

        suspend fun processIndexRow(rowKey: ByteArray): Boolean {
            if (!rowKey.matchesRangePart(0, indexPrefix, sourceLength = rowKey.size, length = indexPrefix.size)) return true
            val valueAndKey = rowKey.copyOfRange(indexPrefix.size, rowKey.size)
            val valueSize = resolveIndexValueSize(valueAndKey, keySize, indexScan.index.indexPartCount) ?: return true
            val rangeLength = indexRangeLength(indexRanges, range, valueSize)

            if (indexScan.direction == DESC && startIndexValue != null) {
                val startComparison = valueAndKey compareTo startIndexValue
                if (startComparison > 0 || (startComparison == 0 && !keyScanRange.includeStart)) return true
            }
            if (indexScan.direction == ASC && range.keyBeforeStart(valueAndKey, length = rangeLength)) return true
            if (indexScan.direction == ASC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
            if (indexScan.direction == DESC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return true
            if (indexScan.direction == DESC && request.startKey == null && range.keyBeforeStart(valueAndKey, length = rangeLength)) return false
            if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true

            val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
            if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return true

            val dedupe = keyBytes.joinToString(",")
            if (!seenKeys.add(dedupe)) return true

            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            } else {
                readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, keyBytes, request.select)
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            }
                ?: return true
            if (request.filterSoftDeleted && record.isDeleted) return true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion, indexScan.index)) return true

            rows += record
            sortingKeys += Bytes(valueAndKey)
            return true
        }

        if (historicRows == null) {
            byteStore.scanInBatches(
                storeName = indexStoreName,
                startKey = startKey,
                includeStart = true,
                endKey = endKey,
                includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                reverse = indexScan.direction == DESC,
                targetLimit = UInt.MAX_VALUE,
            ) { rowKey, _ ->
                processIndexRow(rowKey)
            }
        } else {
            for ((rowKey, _) in historicRows) {
                if (!processIndexRow(rowKey)) break
            }
        }
    }
    val ordered = rows.zip(sortingKeys).let {
        if (limit == UInt.MAX_VALUE) it else it.take(limit.toInt())
    }

    return ScanUpdateRows(
        rows = ordered.map { it.first },
        sortingKeys = ordered.map { it.second },
        dataFetchType = FetchByIndexScan(
            index = indexScan.index.referenceStorageByteArray.bytes,
            direction = indexScan.direction,
            startKey = overallStartKey,
            stopKey = overallStopKey,
        ),
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectUpdateHistoryScanUpdateRows(
    request: ScanUpdatesRequest<DM>,
    keyStoreName: String,
    tableStoreName: String,
    historicTableStoreName: String,
    keyScanRange: KeyScanRanges,
    limit: UInt = request.limit,
): ScanUpdateRows<DM> {
    val rows = mutableListOf<ValuesWithMetaData<DM>>()
    val toVersion = request.toVersion
    byteStore.scanInBatches(storeName = keyStoreName, targetLimit = UInt.MAX_VALUE) { keyBytes, snapshotBytes ->
        if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true
        val record = if (toVersion == null) {
            decodeCurrentSnapshotRecord(
                request.dataModel,
                keyBytes,
                snapshotBytes,
                request.select,
                { qualifier, value -> sensitiveFields.decryptValueIfNeeded(getDataModelId(request.dataModel), keyBytes, qualifier, value) },
            ) ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
        } else {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
        } ?: return@scanInBatches true
        if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true
        rows += record
        true
    }
    rows.sortWith { first, second ->
        val versionComparison = second.lastVersion.compareTo(first.lastVersion)
        if (versionComparison != 0) versionComparison else second.key.bytes.compareTo(first.key.bytes)
    }
    return ScanUpdateRows(
        rows = if (limit == UInt.MAX_VALUE) rows else rows.take(limit.toInt()),
        sortingKeys = null,
        dataFetchType = FetchByUpdateHistoryIndex(),
    )
}

private suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectHardDeleteScanUpdates(
    request: ScanUpdatesRequest<DM>,
    modelId: UInt,
    keyScanRange: KeyScanRanges,
    direction: Direction,
): List<RemovalUpdate<DM>> {
    val hardDeletes = mutableListOf<RemovalUpdate<DM>>()
    val toVersion = request.toVersion ?: ULong.MAX_VALUE
    val ranges = if (direction == ASC) keyScanRange.ranges else keyScanRange.ranges.asReversed()
    rangeLoop@ for (range in ranges) {
        val rangeStart = if (direction == ASC) {
            range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
        } else {
            range.getAscendingStartKey()
        }
        val rangeEnd = if (direction == ASC) {
            range.getDescendingStartKey()
        } else {
            val configuredRangeEnd = range.end
            keyScanRange.startKey?.takeIf { startKey ->
                configuredRangeEnd == null || configuredRangeEnd.isEmpty() || startKey < configuredRangeEnd
            }?.let { startKey ->
                if (keyScanRange.includeStart) keyPrefixUpperBound(startKey) else startKey
            } ?: range.getDescendingStartKey()
        }
        var rawHistoryRow = byteStore.scan(
            storeName = "hdk:$modelId",
            startKey = rangeStart,
            endKey = rangeEnd,
            includeEnd = false,
            reverse = direction == DESC,
            limit = 1u,
        ).firstOrNull()

        while (rawHistoryRow != null && hardDeletes.size.toUInt() < request.limit) {
            hardDeleteHistoryRowReadObserver?.invoke(rawHistoryRow.first)
            val keyBytes = rawHistoryRow.first.copyOfRange(0, rawHistoryRow.first.size - ULong.SIZE_BYTES)
            if (direction == ASC && range.keyOutOfRange(keyBytes)) break
            if (direction == DESC && range.keyBeforeStart(keyBytes)) break

            if (
                keyScanRange.keyWithinRanges(keyBytes, 0) &&
                keyScanRange.matchesPartials(keyBytes, 0) &&
                request.includesHardDeleteStartKey(keyBytes, direction)
            ) {
                val matchingDelete = byteStore.scan(
                    storeName = "hdk:$modelId",
                    startKey = createHardDeleteHistoryRowKey(keyBytes, toVersion),
                    endKey = keyPrefixUpperBound(keyBytes),
                    includeEnd = false,
                    limit = 1u,
                ).firstOrNull()
                if (matchingDelete != null) {
                    hardDeleteHistoryRowReadObserver?.invoke(matchingDelete.first)
                    val version = matchingDelete.first.readTrailingInvertedVersion()
                    if (version >= request.fromVersion) {
                        hardDeletes += RemovalUpdate(
                            key = request.dataModel.key(keyBytes),
                            version = version,
                            reason = HardDelete,
                        )
                    }
                }
            }

            rawHistoryRow = if (direction == ASC) {
                byteStore.scan(
                    storeName = "hdk:$modelId",
                    startKey = keyPrefixUpperBound(keyBytes),
                    endKey = rangeEnd,
                    includeEnd = false,
                    limit = 1u,
                ).firstOrNull()
            } else {
                byteStore.scan(
                    storeName = "hdk:$modelId",
                    startKey = rangeStart,
                    endKey = keyBytes,
                    includeEnd = false,
                    reverse = true,
                    limit = 1u,
                ).firstOrNull()
            }
        }
        if (hardDeletes.size.toUInt() == request.limit) break@rangeLoop
    }
    return hardDeletes
}

private fun ScanUpdatesRequest<*>.includesHardDeleteStartKey(keyBytes: ByteArray, direction: Direction): Boolean {
    val startKey = startKey?.bytes ?: return true
    val comparison = keyBytes.compareTo(startKey)
    return when (direction) {
        ASC -> comparison > 0 || (comparison == 0 && includeStart)
        DESC -> comparison < 0 || (comparison == 0 && includeStart)
    }
}

private suspend fun IndexedDbDataStore.findHardDeleteVersion(
    modelId: UInt,
    keyBytes: ByteArray,
    request: ScanUpdatesRequest<*>,
): ULong? {
    if (request.fromVersion == 0uL) return null

    val toVersion = request.toVersion
    val version = if (toVersion == null) {
        byteStore.get("hd:$modelId", keyBytes)?.readTrailingVersion()
    } else {
        byteStore.scan(
            storeName = "hdk:$modelId",
            startKey = createHardDeleteHistoryRowKey(keyBytes, toVersion),
            endKey = keyPrefixUpperBound(keyBytes),
            includeEnd = false,
            limit = 1u,
        ).firstOrNull()?.first?.readTrailingInvertedVersion()
    }
    return version?.takeIf { it >= request.fromVersion }
}

private data class ScanUpdateCandidates<DM : IsRootDataModel>(
    val rows: List<ValuesWithMetaData<DM>>,
    val sortingKeys: List<Bytes>?,
    val hardDeletes: List<RemovalUpdate<DM>>,
)

private fun <DM : IsRootDataModel> selectScanUpdateCandidates(
    scanRows: ScanUpdateRows<DM>,
    hardDeletes: List<RemovalUpdate<DM>>,
    limit: UInt,
): ScanUpdateCandidates<DM> {
    if (hardDeletes.isEmpty()) return ScanUpdateCandidates(scanRows.rows, scanRows.sortingKeys, hardDeletes)

    val rowsByKey = scanRows.rows.associateBy { it.key }
    val deletionsByKey = hardDeletes.groupBy { it.key }
    val selectedKeys = rowsByKey.keys.union(deletionsByKey.keys)
        .sortedWith { first, second ->
            if (scanRows.dataFetchType is FetchByTableScan) {
                val comparison = first.bytes.compareTo(second.bytes)
                return@sortedWith if (scanRows.dataFetchType.direction == ASC) comparison else -comparison
            }
            val firstVersion = maxOf(
                rowsByKey[first]?.lastVersion ?: 0uL,
                deletionsByKey[first]?.maxOf { it.version } ?: 0uL,
            )
            val secondVersion = maxOf(
                rowsByKey[second]?.lastVersion ?: 0uL,
                deletionsByKey[second]?.maxOf { it.version } ?: 0uL,
            )
            val versionComparison = secondVersion.compareTo(firstVersion)
            if (versionComparison != 0) versionComparison else second.bytes.compareTo(first.bytes)
        }
        .take(limit.toInt())
        .toSet()

    val selectedRows = scanRows.rows.withIndex().filter { it.value.key in selectedKeys }
    return ScanUpdateCandidates(
        rows = selectedRows.map { it.value },
        sortingKeys = scanRows.sortingKeys?.let { sortingKeys -> selectedRows.map { sortingKeys[it.index] } },
        hardDeletes = hardDeletes.filter { it.key in selectedKeys },
    )
}
