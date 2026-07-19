package maryk.datastore.indexeddb.processors

import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.scanInBatches
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processScanRequest(
    storeAction: ScanStoreAction<DM>,
) {
    val request = storeAction.request
    request.checkToVersion(keepAllVersions)

    val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
    if (keyScanRange.ranges.isEmpty()) {
        storeAction.response.complete(
            ValuesResponse(
                dataModel = request.dataModel,
                values = emptyList(),
                dataFetchType = FetchByTableScan(ASC, null, null),
            )
        )
        return
    }

    val modelId = getDataModelId(request.dataModel)
    val keyStoreName = "k:$modelId"
    val tableStoreName = "t:$modelId"
    val uniqueStoreName = "u:$modelId"
    val historicTableStoreName = "ht:$modelId"
    val historicUniqueStoreName = "hu:$modelId"
    val aggregator = request.aggregations?.let(::Aggregator)
    val values = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))

    if (keyScanRange.isSingleKey()) {
        val keyBytes = keyScanRange.ranges.first().start
        val toVersion = request.toVersion
        val record = if (toVersion != null) {
            readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
        } else {
            readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, keyBytes, request.select)
                ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
        }
        if (
            record != null &&
            (!request.filterSoftDeleted || !record.isDeleted) &&
            valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)
        ) {
            values += record
            aggregator?.aggregate { reference -> record.values[reference] }
        }

        storeAction.response.complete(
            ValuesResponse(
                dataModel = request.dataModel,
                values = values,
                aggregations = aggregator?.toResponse(),
                dataFetchType = FetchByKey,
            )
        )
        return
    }

    keyScanRange.uniques?.firstOrNull()?.let { uniqueToMatch ->
        @Suppress("UNCHECKED_CAST")
        val uniqueValue = Value.castDefinition(uniqueToMatch.definition).toStorageBytes(
            uniqueToMatch.value as Comparable<Any>,
            TypeIndicator.NoTypeIndicator.byte
        )
        val mappedUniqueValues = sensitiveFields.mapUniqueValueByteCandidates(
            modelId,
            uniqueToMatch.reference,
            uniqueValue,
        )
        var keyBytes: ByteArray? = null
        for (mappedUniqueValue in mappedUniqueValues) {
            val uniqueKey = createUniqueRowKey(uniqueToMatch.reference, mappedUniqueValue)
            val resolvedKey = request.toVersion?.let { toVersion ->
                byteStore.readHistoricUniqueKey(historicUniqueStoreName, uniqueKey, toVersion)
            } ?: if (request.toVersion == null) {
                byteStore.get(uniqueStoreName, uniqueKey)
            } else {
                null
            }
            if (resolvedKey != null) {
                keyBytes = resolvedKey
                break
            }
        }
        if (keyBytes != null) {
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            } else {
                readCurrentSnapshotDecrypted(byteStore, request.dataModel, keyStoreName, keyBytes, request.select)
                    ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            }
            if (
                record != null &&
                (!request.filterSoftDeleted || !record.isDeleted) &&
                valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)
            ) {
                values += record
                aggregator?.aggregate { reference -> record.values[reference] }
            }
        }

        storeAction.response.complete(
            ValuesResponse(
                dataModel = request.dataModel,
                values = values,
                aggregations = aggregator?.toResponse(),
                dataFetchType = FetchByUniqueKey(uniqueToMatch.reference),
            )
        )
        return
    }

    val requestedScanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)
    val scanType = if (requestedScanType is TableScan) {
        request.dataModel.optimizeTableScan(
            requestedScanType,
            keyScanRange,
            filter = request.where,
            allowTableScan = request.allowTableScan
        )
    } else {
        requestedScanType
    }

    if (scanType is IndexScan) {
        storeAction.response.complete(
            processIndexScan(
                request = request,
                modelId = modelId,
                keyStoreName = keyStoreName,
                tableStoreName = tableStoreName,
                indexStoreName = "i:$modelId",
                historicTableStoreName = historicTableStoreName,
                historicIndexStoreName = "hi:$modelId",
                keyScanRange = keyScanRange,
                indexScan = scanType,
                aggregator = aggregator,
            )
        )
        return
    }

    require(scanType is TableScan)

    val overallStartKey: ByteArray?
    val overallEndKey: ByteArray?

    when (scanType.direction) {
        ASC -> {
            overallStartKey = keyScanRange.ranges.first().getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
            overallEndKey = keyScanRange.ranges.last().getDescendingStartKey()

            rangeLoop@ for (range in keyScanRange.ranges) {
                val startKey = range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                byteStore.scanInBatches(
                    storeName = keyStoreName,
                    startKey = startKey,
                    endKey = range.end,
                    includeEnd = range.endInclusive,
                    targetLimit = UInt.MAX_VALUE,
                ) { keyBytes, snapshotBytes ->
                    if (range.keyOutOfRange(keyBytes)) return@scanInBatches false
                    if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                    val toVersion = request.toVersion
                    val record = if (toVersion != null) {
                        readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                    } else {
                        decodeCurrentSnapshotRecord(
                            request.dataModel,
                            keyBytes,
                            snapshotBytes,
                            request.select,
                            sensitiveFields::decryptValueIfNeeded,
                        )
                            ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                    }
                        ?: return@scanInBatches true
                    if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                    if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                    values += record
                    aggregator?.aggregate { reference -> record.values[reference] }
                    values.size.toUInt() < request.limit
                }
                if (values.size.toUInt() == request.limit) break@rangeLoop
            }
        }
        DESC -> {
            overallStartKey = keyScanRange.ranges.first().getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
            overallEndKey = keyScanRange.ranges.last().getAscendingStartKey()

            rangeLoop@ for (range in keyScanRange.ranges.asReversed()) {
                val upperKey = range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                byteStore.scanInBatches(
                    storeName = keyStoreName,
                    startKey = range.start.takeUnless { it.isEmpty() },
                    includeStart = range.startInclusive,
                    endKey = upperKey,
                    reverse = true,
                    targetLimit = UInt.MAX_VALUE,
                ) { keyBytes, snapshotBytes ->
                    if (range.keyBeforeStart(keyBytes)) return@scanInBatches false
                    if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                    val toVersion = request.toVersion
                    val record = if (toVersion != null) {
                        readHistoricRecordDecrypted(byteStore, request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                    } else {
                        decodeCurrentSnapshotRecord(
                            request.dataModel,
                            keyBytes,
                            snapshotBytes,
                            request.select,
                            sensitiveFields::decryptValueIfNeeded,
                        )
                            ?: readRecordDecrypted(byteStore, request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                    }
                        ?: return@scanInBatches true
                    if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                    if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                    values += record
                    aggregator?.aggregate { reference -> record.values[reference] }
                    values.size.toUInt() < request.limit
                }
                if (values.size.toUInt() == request.limit) break@rangeLoop
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = request.dataModel,
            values = values,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByTableScan(
                direction = scanType.direction,
                startKey = overallStartKey,
                stopKey = overallEndKey,
            )
        )
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processIndexScan(
    request: ScanRequest<DM>,
    modelId: UInt,
    keyStoreName: String,
    tableStoreName: String,
    indexStoreName: String,
    historicTableStoreName: String,
    historicIndexStoreName: String,
    keyScanRange: KeyScanRanges,
    indexScan: IndexScan,
    aggregator: Aggregator?,
): ValuesResponse<DM> {
    val values = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))
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
    for (range in rangeList) {
        val startKey = when (indexScan.direction) {
            ASC -> createIndexKeyWithPrefix(
                indexPrefix,
                startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
            )
            DESC -> createIndexKeyWithPrefix(indexPrefix, range.getAscendingStartKey())
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
            DESC -> when (val descendingStart = range.getDescendingStartKey(startIndexValue, keyScanRange.includeStart)) {
                null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                else -> if (descendingStart.isEmpty()) {
                    createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                } else {
                    createIndexKeyWithPrefix(indexPrefix, descendingStart)
                }
            }
        }

        val rows = request.toVersion?.let { toVersion ->
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
            if (startIndexValue != null) {
                val comparison = valueAndKey compareTo startIndexValue
                if (indexScan.direction == ASC && (comparison < 0 || (comparison == 0 && !request.includeStart))) return true
                if (indexScan.direction == DESC && (comparison > 0 || (comparison == 0 && !request.includeStart))) return true
            }
            val rangeLength = indexRangeLength(indexRanges, range, valueSize)

            if (indexScan.direction == ASC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
            if (indexScan.direction == DESC && startIndexValue == null && range.keyBeforeStart(valueAndKey, length = rangeLength)) return false
            if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true

            val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
            if (!indexKeyScanRange.keyWithinRanges(keyBytes, 0) || !indexKeyScanRange.matchesPartials(keyBytes, 0)) return true

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

            values += record
            aggregator?.aggregate { reference -> record.values[reference] }
            return values.size.toUInt() < request.limit
        }

        if (rows == null) {
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
            for ((rowKey, _) in rows) {
                if (!processIndexRow(rowKey)) break
            }
        }
        if (values.size.toUInt() == request.limit) {
            return ValuesResponse(
                dataModel = request.dataModel,
                values = values,
                aggregations = aggregator?.toResponse(),
                dataFetchType = FetchByIndexScan(
                    index = indexScan.index.referenceStorageByteArray.bytes,
                    direction = indexScan.direction,
                    startKey = overallStartKey,
                    stopKey = overallStopKey,
                )
            )
        }
    }

    return ValuesResponse(
        dataModel = request.dataModel,
        values = values,
        aggregations = aggregator?.toResponse(),
        dataFetchType = FetchByIndexScan(
            index = indexScan.index.referenceStorageByteArray.bytes,
            direction = indexScan.direction,
            startKey = overallStartKey,
            stopKey = overallStopKey,
        )
    )
}

