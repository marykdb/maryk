package maryk.datastore.rocksdb.processors

import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsRootModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.key
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.getKeyByUniqueValue
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal fun <DM : IsRootModel> processScan(
    scanRequest: IsScanRequest<DM, *>,
    dataStore: RocksDBDataStore,
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    scanSetup: ((ScanType) -> Unit)? = null,
    processRecord: (Key<DM>, ULong, ByteArray?) -> Unit
) {
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    when {
        // If hard key match then quit with direct record
        keyScanRange.isSingleKey() -> {
            val key = scanRequest.dataModel.key(keyScanRange.ranges.first().start)
            val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)
            if (mayExist) {
                val valueLength = dbAccessor.get(columnFamilies.keys, readOptions, key.bytes, recyclableByteArray)
                // Only process it if it was created
                if (valueLength != rocksDBNotFound) {
                    val createdVersion = recyclableByteArray.readVersionBytes()
                    if (shouldProcessRecord(dbAccessor, columnFamilies, readOptions, key, createdVersion, scanRequest, keyScanRange)) {
                        processRecord(key, createdVersion, null)
                    }
                }
            }
        }
        else -> {
            // Process uniques as a fast path
            keyScanRange.uniques?.let {
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

                    getKeyByUniqueValue(dbAccessor, columnFamilies, readOptions, reference, scanRequest.toVersion) { keyReader, setAtVersion ->
                        @Suppress("UNCHECKED_CAST")
                        val key = scanRequest.dataModel.Model.key(keyReader) as Key<DM>

                        if (shouldProcessRecord(dbAccessor, columnFamilies, readOptions, key, setAtVersion, scanRequest, keyScanRange)) {
                            readCreationVersion(dbAccessor, columnFamilies, readOptions, key.bytes)?.let { createdVersion ->
                                processRecord(key, createdVersion, null)
                            }
                        }
                    }
                    return
                }
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(scanIndex, keyScanRange.equalPairs)
            } else scanIndex

            scanSetup?.invoke(processedScanIndex)

            when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        dataStore,
                        dbAccessor,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex.direction,
                        keyScanRange,
                        processRecord
                    )
                }
                is IndexScan -> {
                    scanIndex(
                        dataStore,
                        dbAccessor,
                        columnFamilies,
                        scanRequest,
                        processedScanIndex,
                        keyScanRange,
                        processRecord
                    )
                }
            }
        }
    }
}

internal fun <DM: IsRootModel> shouldProcessRecord(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    createdVersion: ULong?,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges
): Boolean {
    if (createdVersion == null) {
        // record was not created
        return false
    } else if (scanRange.keyBeforeStart(key.bytes) || !scanRange.keyWithinRanges(key.bytes, 0) || !scanRange.matchesPartials(key.bytes)) {
        return false
    }

    return !scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, readOptions, key.bytes, 0, key.size, createdVersion, scanRequest.toVersion)
}
