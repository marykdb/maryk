package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.orders.Direction.ASC
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.createIndexKeyPrefix
import maryk.datastore.rocksdb.processors.helpers.getKeyByUniqueValue
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.ScanType.UpdateHistoryScan
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

/** Walk with [scanRequest] on [RocksDBDataStore] and do [processRecord] */
internal fun <DM : IsRootDataModel> RocksDBDataStore.processScan(
    scanRequest: IsScanRequest<DM, *>,
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    includeSortingKey: Boolean = false,
    softDeleteCache: RequestKeySoftDeleteCache? = null,
    historicalReader: HistoricalTableReader? = null,
    scanSetup: ((ScanType) -> Unit)? = null,
    processRecord: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val dataModelId = getDataModelId(scanRequest.dataModel)
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(keepAllVersions)

    if (keyScanRange.ranges.isEmpty()) {
        return FetchByTableScan(ASC, null, null)
    }

    when {
        // If hard key match then quit with direct record
        keyScanRange.isSingleKey() -> {
            val key = scanRequest.dataModel.key(keyScanRange.ranges.first().start)
            val createdVersion = readCreationVersion(
                dbAccessor,
                columnFamilies,
                readOptions,
                key.bytes,
                scanRequest.toVersion
            )
            if (createdVersion != null) {
                if (shouldProcessRecord(dbAccessor, columnFamilies, readOptions, key, createdVersion, scanRequest, keyScanRange, softDeleteCache, historicalReader)) {
                    processRecord(key, createdVersion, null)
                }
            }
            return FetchByKey
        }
        else -> {
            // Process uniques as a fast path
            keyScanRange.uniques?.takeIf { it.isNotEmpty() }?.let { uniqueMatchers ->
                // Only process the first unique since it has to match every found unique matcher
                // and if first is set it can go to direct key to match further
                val firstMatcher = uniqueMatchers.first()
                @Suppress("UNCHECKED_CAST")
                val valueBytes = (firstMatcher.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>)
                    .toStorageBytes(firstMatcher.value as Comparable<Any>)
                val rawUniqueValue = ByteArray(1 + valueBytes.size).apply {
                    this[0] = TypeIndicator.NoTypeIndicator.byte
                    valueBytes.copyInto(this, 1)
                }
                val uniqueValues = mapUniqueValueByteCandidates(
                    dataModelId,
                    firstMatcher.reference,
                    rawUniqueValue,
                )

                for (uniqueValue in uniqueValues) {
                    val reference = ByteArray(firstMatcher.reference.size + uniqueValue.size).apply {
                        firstMatcher.reference.copyInto(this)
                        uniqueValue.copyInto(this, firstMatcher.reference.size)
                    }

                    val found = getKeyByUniqueValue(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies,
                        readOptions = readOptions,
                        reference = reference,
                        keySize = scanRequest.dataModel.Meta.keyByteSize,
                        toVersion = scanRequest.toVersion
                    ) { keyReader, setAtVersion ->
                        val key = scanRequest.dataModel.key(keyReader)

                        if (shouldProcessRecord(dbAccessor, columnFamilies, readOptions, key, setAtVersion, scanRequest, keyScanRange, softDeleteCache, historicalReader)) {
                            (softDeleteCache?.getCreationVersion(key.bytes)
                                ?: readCreationVersion(
                                    dbAccessor,
                                    columnFamilies,
                                    readOptions,
                                    key.bytes,
                                    scanRequest.toVersion
                                ))?.let { createdVersion ->
                                processRecord(key, createdVersion, null)
                            }
                        }
                    }
                    if (found) break
                }
                return FetchByUniqueKey(firstMatcher.reference)
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(
                    scanIndex,
                    keyScanRange,
                    filter = scanRequest.where,
                    allowTableScan = scanRequest.allowTableScan
                )
            } else scanIndex

            scanSetup?.invoke(processedScanIndex)

            return when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        dbAccessor,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex.direction,
                        keyScanRange,
                        historicalReader,
                        processRecord
                    )
                }
                is IndexScan -> {
                    var processedRecords = 0u
                    val dataFetchType = scanIndex(
                        dbAccessor,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex,
                        keyScanRange,
                        includeSortingKey,
                        softDeleteCache,
                        historicalReader
                    ) { key, createdVersion, sortingKey ->
                        processedRecords++
                        processRecord(key, createdVersion, sortingKey)
                    }

                    if (
                        processedRecords == 0u &&
                        scanRequest.allowTableScan &&
                        isIndexUnavailableForReference(
                            dbAccessor = dbAccessor,
                            columnFamilies = columnFamilies,
                            readOptions = readOptions,
                            indexReference = processedScanIndex.index.referenceStorageByteArray.bytes,
                            toVersion = scanRequest.toVersion
                        )
                    ) {
                        scanStore(
                            dbAccessor,
                            columnFamilies,
                            scanRequest,
                            processedScanIndex.direction,
                            keyScanRange,
                            historicalReader,
                            processRecord
                        )
                    } else {
                        dataFetchType
                    }
                }
                is UpdateHistoryScan -> throw IllegalStateException("UpdateHistoryScan is only supported by scanUpdates")
            }
        }
    }
}

internal fun <DM: IsRootDataModel> shouldProcessRecord(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    createdVersion: ULong?,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges,
    softDeleteCache: RequestKeySoftDeleteCache? = null,
    historicalReader: HistoricalTableReader? = null
): Boolean {
    if (createdVersion == null) {
        // record was not created
        return false
    } else if (scanRange.keyBeforeStart(key.bytes) || !scanRange.keyWithinRanges(key.bytes, 0) || !scanRange.matchesPartials(key.bytes)) {
        return false
    }

    return !scanRequest.shouldBeFiltered(
        dbAccessor,
        columnFamilies,
        readOptions,
        key.bytes,
        0,
        key.size,
        createdVersion,
        scanRequest.toVersion,
        historicalReader = historicalReader,
        softDeleteCache = softDeleteCache
    )
}

private fun isIndexUnavailableForReference(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    indexReference: ByteArray,
    toVersion: ULong?
): Boolean {
    val columnFamily = if (toVersion == null) {
        columnFamilies.index
    } else {
        (columnFamilies as? HistoricTableColumnFamilies)?.historic?.index ?: return false
    }
    val indexKeyPrefix = createIndexKeyPrefix(indexReference)

    dbAccessor.getIterator(readOptions, columnFamily).use { iterator ->
        iterator.seek(indexKeyPrefix)
        return !iterator.isValid() || !iterator.key().matchesRangePart(0, indexKeyPrefix, length = indexKeyPrefix.size)
    }
}
