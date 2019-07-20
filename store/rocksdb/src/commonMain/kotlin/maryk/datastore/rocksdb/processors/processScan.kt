package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.getKeyByUniqueValue
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processScan(
    scanRequest: IsScanRequest<DM, P, *>,
    dataStore: RocksDBDataStore,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    processRecord: (Key<DM>, ULong) -> Unit
) {
    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes)

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    when {
        // If hard key match then quit with direct record
        scanRange.isSingleKey() -> {
            @Suppress("UNCHECKED_CAST")
            val key = scanRequest.dataModel.key(scanRange.ranges.first().start) as Key<DM>
            val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, StringBuilder())
            if (mayExist) {
                val createdVersion = transaction.get(columnFamilies.keys, readOptions, key.bytes)?.toULong()
                // Only process it if it was created
                if (createdVersion != null) {
                    if (shouldProcessRecord(transaction, columnFamilies, readOptions, key, createdVersion, scanRequest, scanRange)) {
                        processRecord(key, createdVersion)
                    }
                }
            }
        }
        else -> {
            // Process uniques as a fast path
            scanRange.uniques?.let {
                if (it.isNotEmpty()) {
                    // Only process the first unique since it has to match every found unique matcher
                    // and if first is set it can go to direct key to match further
                    val firstMatcher = it.first()

                    val uniqueReference = firstMatcher.reference
                    @Suppress("UNCHECKED_CAST")
                    val value = firstMatcher.value as Comparable<Any>
                    @Suppress("UNCHECKED_CAST")
                    val valueBytes = (firstMatcher.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>).toStorageBytes(value)

                    val reference = ByteArray(uniqueReference.size + 1 +valueBytes.size)
                    uniqueReference.copyInto(reference)
                    reference[uniqueReference.size] = NO_TYPE_INDICATOR
                    valueBytes.copyInto(reference, uniqueReference.size + 1, 0, valueBytes.size)

                    getKeyByUniqueValue(transaction, columnFamilies, readOptions, reference, scanRequest.toVersion) { keyReader, setAtVersion ->
                        @Suppress("UNCHECKED_CAST")
                        val key = scanRequest.dataModel.key(keyReader) as Key<DM>

                        if (shouldProcessRecord(transaction, columnFamilies, readOptions, key, setAtVersion, scanRequest, scanRange)) {
                            readCreationVersion(transaction, columnFamilies, readOptions, key.bytes)?.let { createdVersion ->
                                processRecord(key, createdVersion)
                            }
                        }
                    }
                    return
                }
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, scanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(scanIndex, scanRange.equalPairs)
            } else scanIndex

            when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        transaction,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex.direction,
                        scanRange,
                        processRecord
                    )
                }
                is IndexScan -> {
                    scanIndex(
                        dataStore,
                        transaction,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex,
                        scanRange,
                        processRecord
                    )
                }
            }
        }
    }
}

internal fun <DM: IsRootValuesDataModel<P>, P:PropertyDefinitions> shouldProcessRecord(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    createdVersion: ULong?,
    scanRequest: IsScanRequest<DM, P, *>,
    scanRange: KeyScanRanges
): Boolean {
    if (createdVersion == null) {
        // record was not created
        return false
    } else if (!scanRange.keyWithinRanges(key.bytes, 0)) {
        return false
    } else if (!scanRange.matchesPartials(key.bytes)) {
        return false
    }

    return !scanRequest.shouldBeFiltered(transaction, columnFamilies, readOptions, key.bytes, 0, key.size, createdVersion, scanRequest.toVersion)
}
