package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.scanInBatches
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.orderToScanType
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)
    if (!keepAllVersions && request.maxVersions > 1u) {
        throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
    }

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val indexStoreName = "i:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val historicIndexStoreName = "hi:$modelId"
    val changeStoreName = "c:$modelId"
    val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
    val changes = mutableListOf<DataObjectVersionedChange<DM>>()
    val scanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)
    if (scanType is IndexScan) {
        storeAction.response.complete(
            processIndexScanChanges(
                request = request,
                keyStoreName = keyStoreName,
                tableStoreName = tableStoreName,
                indexStoreName = indexStoreName,
                historicTableStoreName = historicTableStoreName,
                historicIndexStoreName = historicIndexStoreName,
                changeStoreName = changeStoreName,
                keyScanRange = keyScanRange,
                indexScan = scanType,
            )
        )
        return
    }

    val direction = scanType as? TableScan
    val scanDirection = direction?.direction ?: ASC
    val overallStartKey = when (scanDirection) {
        ASC -> request.startKey?.bytes ?: keyScanRange.ranges.firstOrNull()?.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
        DESC -> keyScanRange.ranges.firstOrNull()?.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
    }
    val overallStopKey = when (scanDirection) {
        ASC -> keyScanRange.ranges.lastOrNull()?.getDescendingStartKey()
        DESC -> keyScanRange.ranges.lastOrNull()?.getAscendingStartKey()
    }
    val ranges = if (scanDirection == ASC) keyScanRange.ranges else keyScanRange.ranges.asReversed()
    rangeLoop@ for (range in ranges) {
        val startKey = when (scanDirection) {
            ASC -> range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
            DESC -> range.start.takeUnless { it.isEmpty() }
        }
        val endKey = when (scanDirection) {
            ASC -> range.end
            DESC -> range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
        }
        byteStore.scanInBatches(
            storeName = keyStoreName,
            startKey = startKey,
            includeStart = if (scanDirection == ASC) range.startInclusive else range.startInclusive,
            endKey = endKey,
            includeEnd = if (scanDirection == ASC) range.endInclusive else true,
            reverse = scanDirection == DESC,
            targetLimit = UInt.MAX_VALUE,
        ) { keyBytes, rowValue ->
            if (scanDirection == ASC && range.keyOutOfRange(keyBytes)) return@scanInBatches false
            if (scanDirection == DESC && range.keyBeforeStart(keyBytes)) return@scanInBatches false
            if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

            val toVersion = request.toVersion
            val record = if (toVersion == null) {
                decodeCurrentSnapshotRecord(request.dataModel, keyBytes, rowValue, null, sensitiveFields::decryptValueIfNeeded)
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, null)
            } else {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, null)
            }
                ?: return@scanInBatches true
            if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

            val versionedChanges = byteStore.readChangeLog(
                dataModel = request.dataModel,
                changeStoreName = changeStoreName,
                historicTableStoreName = historicTableStoreName,
                keyBytes = keyBytes,
                fromVersion = request.fromVersion,
                toVersion = request.toVersion,
                maxVersions = request.maxVersions,
                select = request.select,
                decryptValue = sensitiveFields::decryptValueIfNeeded,
            )
            if (versionedChanges.isEmpty()) return@scanInBatches true

            changes += DataObjectVersionedChange(
                key = request.dataModel.key(keyBytes),
                changes = versionedChanges,
            )
            changes.size.toUInt() < request.limit
        }
        if (changes.size.toUInt() == request.limit) break@rangeLoop
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = request.dataModel,
            changes = changes,
            dataFetchType = FetchByTableScan(
                direction = scanDirection,
                startKey = overallStartKey,
                stopKey = overallStopKey,
            ),
        )
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processIndexScanChanges(
    request: ScanChangesRequest<DM>,
    keyStoreName: String,
    tableStoreName: String,
    indexStoreName: String,
    historicTableStoreName: String,
    historicIndexStoreName: String,
    changeStoreName: String,
    keyScanRange: KeyScanRanges,
    indexScan: IndexScan,
): ChangesResponse<DM> {
    val changes = ArrayList<DataObjectVersionedChange<DM>>(request.limit.toInt().coerceAtLeast(4))
    val seenKeys = mutableSetOf<String>()
    val keySize = request.dataModel.Meta.keyByteSize
    val indexPrefix = createIndexKeyPrefix(indexScan.index.referenceStorageByteArray.bytes)
    val toVersion = request.toVersion
    val indexKeyScanRange = if (request.startKey == null) {
        keyScanRange
    } else {
        request.dataModel.createScanRange(request.where, null, request.includeStart)
    }
    val baseIndexRanges = indexScan.index.createScanRange(request.where, indexKeyScanRange)
    val startIndexValue = request.startKey?.let { startKey ->
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
        val startKey = if (indexScan.direction == ASC) {
            createIndexKeyWithPrefix(
                indexPrefix,
                startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
            )
        } else {
            indexPrefix
        }
        val endKey = if (indexScan.direction == ASC) {
            when (val rangeEnd = range.getDescendingStartKey()) {
                null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                else -> if (rangeEnd.isEmpty()) {
                    createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                } else {
                    createIndexKeyWithPrefix(indexPrefix, rangeEnd)
                }
            }
        } else {
            startIndexValue?.let {
                keyPrefixUpperBound(createIndexKeyWithPrefix(indexPrefix, it))
            } ?: createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
        }

        val historicRows = toVersion?.let {
            byteStore.readHistoricIndexRows(
                storeName = historicIndexStoreName,
                startKey = startKey,
                endKey = endKey,
                includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                toVersion = it,
                reverse = indexScan.direction == DESC,
            )
        }

        suspend fun processIndexChangeRow(rowKey: ByteArray): Boolean {
            if (!rowKey.matchesRangePart(0, indexPrefix, sourceLength = rowKey.size, length = indexPrefix.size)) return true
            val valueAndKey = rowKey.copyOfRange(indexPrefix.size, rowKey.size)
            val valueSize = resolveIndexValueSize(valueAndKey, keySize, indexScan.index.indexPartCount) ?: return true
            if (startIndexValue != null) {
                val comparison = valueAndKey compareTo startIndexValue
                if (indexScan.direction == ASC && (comparison < 0 || (comparison == 0 && !request.includeStart))) return true
                if (indexScan.direction == DESC && (comparison > 0 || (comparison == 0 && !request.includeStart))) return true
            }
            if (indexScan.direction == ASC) {
                val rangeLength = indexRangeLength(indexRanges, range, valueSize)
                if (range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
                if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true
            } else if (startIndexValue != null) {
                val comparison = valueAndKey compareTo startIndexValue
                if (comparison > 0 || (comparison == 0 && !keyScanRange.includeStart)) return true
            }

            val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
            if (!indexKeyScanRange.keyWithinRanges(keyBytes, 0) || !indexKeyScanRange.matchesPartials(keyBytes, 0)) return true

            val dedupe = keyBytes.joinToString(",")
            if (!seenKeys.add(dedupe)) return true

            val record = if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, null)
            } else {
                readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, keyBytes, null)
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, null)
            } ?: return true
            if (request.filterSoftDeleted && record.isDeleted) return true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion, indexScan.index)) return true

            val versionedChanges = byteStore.readChangeLog(
                dataModel = request.dataModel,
                changeStoreName = changeStoreName,
                historicTableStoreName = historicTableStoreName,
                keyBytes = keyBytes,
                fromVersion = request.fromVersion,
                toVersion = request.toVersion,
                maxVersions = request.maxVersions,
                select = request.select,
                decryptValue = sensitiveFields::decryptValueIfNeeded,
            ).ifEmpty {
                record.toCreationChanges(request.fromVersion, request.toVersion, request.select)
            }
            if (versionedChanges.isEmpty()) return true

            changes += DataObjectVersionedChange(
                key = request.dataModel.key(keyBytes),
                sortingKey = Bytes(valueAndKey),
                changes = versionedChanges,
            )
            return changes.size.toUInt() < request.limit
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
                processIndexChangeRow(rowKey)
            }
        } else {
            for ((rowKey, _) in historicRows) {
                if (!processIndexChangeRow(rowKey)) break@rangeLoop
            }
        }
        if (changes.size.toUInt() == request.limit) break@rangeLoop
    }

    return ChangesResponse(
        dataModel = request.dataModel,
        changes = changes,
        dataFetchType = FetchByIndexScan(
            index = indexScan.index.referenceStorageByteArray.bytes,
            direction = indexScan.direction,
            startKey = overallStartKey,
            stopKey = overallStopKey,
        ),
    )
}

