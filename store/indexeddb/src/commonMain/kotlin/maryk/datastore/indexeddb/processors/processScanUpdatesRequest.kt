package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
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

    val scanRows = when {
        request.canUseUpdateHistoryIndex() && keepUpdateHistoryIndex -> collectUpdateHistoryScanUpdateRows(
            request = request,
            modelId = modelId,
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            historicTableStoreName = historicTableStoreName,
            keyScanRange = keyScanRange,
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
        )
        else -> collectTableScanUpdateRows(
            request = request,
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            historicTableStoreName = historicTableStoreName,
            keyScanRange = keyScanRange,
            tableScan = scanType as? TableScan ?: TableScan(ASC),
        )
    }

    val rows = scanRows.rows
    val highestVersion = minOf(request.toVersion ?: ULong.MAX_VALUE, rows.maxOfOrNull { it.lastVersion } ?: 0uL)
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    val matchingKeys = rows.map { it.key }
    updates += OrderedKeysUpdate(matchingKeys, highestVersion, scanRows.sortingKeys)
    rows.forEachIndexed { index, record ->
        val versionedChanges = byteStore.readChangeLog(
            dataModel = request.dataModel,
            changeStoreName = changeStoreName,
            historicTableStoreName = historicTableStoreName,
            keyBytes = record.key.bytes,
            fromVersion = request.fromVersion,
            toVersion = request.toVersion,
            maxVersions = request.maxVersions,
            select = request.select,
            decryptValue = sensitiveFields::decryptValueIfNeeded,
        )
        for (versionedChange in versionedChanges) {
            if (versionedChange.changes.any { it is ObjectCreate } || request.orderedKeys?.contains(record.key) == false) {
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
    request.orderedKeys?.let { orderedKeys ->
        val matchingSet = matchingKeys.toSet()
        val orderedSet = orderedKeys.toSet()
        for (removedKey in orderedKeys.filter { it !in matchingSet }) {
            val meta = byteStore.get(keyStoreName, removedKey.bytes)?.let(::decodeRecordMeta)
            updates += RemovalUpdate(
                key = removedKey,
                version = highestVersion,
                reason = when {
                    meta == null -> HardDelete
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
                    insertionIndex = matchingKeys.indexOf(addedRecord.key),
                    isDeleted = addedRecord.isDeleted,
                    values = addedRecord.values,
                )
            }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = request.dataModel,
            updates = updates,
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
                    sensitiveFields::decryptValueIfNeeded,
                )
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            } else {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            }
                ?: return@scanInBatches true
            if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

            rows += record
            rows.size.toUInt() < request.limit
        }
        if (rows.size.toUInt() == request.limit) break@rangeLoop
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
    val ordered = rows.zip(sortingKeys).take(request.limit.toInt())

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
    modelId: UInt,
    keyStoreName: String,
    tableStoreName: String,
    historicTableStoreName: String,
    keyScanRange: KeyScanRanges,
): ScanUpdateRows<DM> {
    val rows = mutableListOf<ValuesWithMetaData<DM>>()
    byteStore.scanInBatches(storeName = keyStoreName, targetLimit = UInt.MAX_VALUE) { keyBytes, snapshotBytes ->
        if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true
        val toVersion = request.toVersion
        val record = if (toVersion == null) {
            decodeCurrentSnapshotRecord(
                request.dataModel,
                keyBytes,
                snapshotBytes,
                request.select,
                sensitiveFields::decryptValueIfNeeded,
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
    if (rows.size.toUInt() > request.limit) {
        rows.subList(request.limit.toInt(), rows.size).clear()
    }
    return ScanUpdateRows(
        rows = rows,
        sortingKeys = null,
        dataFetchType = FetchByUpdateHistoryIndex(),
    )
}

