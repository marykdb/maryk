package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Transaction
import maryk.core.clock.HLC
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
import maryk.core.query.responses.FetchByUniqueKey
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.getKeyByUniqueValue
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScan(
    scanRequest: IsScanRequest<DM, *>,
    tableDirs: IsTableDirectories,
    scanSetup: ((ScanType) -> Unit)? = null,
    processRecord: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(keepAllVersions)

    // If hard key match then quit with direct record
    if (keyScanRange.isSingleKey()) {
        val key = scanRequest.dataModel.key(keyScanRange.ranges.first().start)
        return this.runTransaction { tr ->
            val exists = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
            if (exists != null) {
                val createdVersion = HLC.fromStorageBytes(exists).timestamp
                if (shouldProcessRecord(tr, tableDirs, key, createdVersion, scanRequest, keyScanRange)) {
                    processRecord(key, createdVersion, null)
                }
            }
            FetchByKey
        }
    }

    // Fast path for unique lookups: resolve unique -> key directly (supports toVersion).
    keyScanRange.uniques?.takeIf { it.isNotEmpty() }?.let { uniqueMatchers ->
        val firstMatcher = uniqueMatchers.first()
        @Suppress("UNCHECKED_CAST")
        val valueBytes = (firstMatcher.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>)
            .toStorageBytes(firstMatcher.value as Comparable<Any>)

        // Build (reference || NoTypeIndicator || valueBytes) to match how uniques are stored
        val reference = ByteArray(firstMatcher.reference.size + 1 + valueBytes.size).apply {
            firstMatcher.reference.copyInto(this)
            this[firstMatcher.reference.size] = TypeIndicator.NoTypeIndicator.byte
            valueBytes.copyInto(this, firstMatcher.reference.size + 1)
        }

        return this.runTransaction { tr ->
            tr.getKeyByUniqueValue(tableDirs, reference, scanRequest.toVersion) { keyBytes, setAtVersion ->
                val key = scanRequest.dataModel.key(keyBytes)
                if (shouldProcessRecord(tr, tableDirs, key, setAtVersion, scanRequest, keyScanRange)) {
                    // Ensure we have the creation version for processRecord callback
                    val createdBytes = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
                    val createdVersion = createdBytes?.let { HLC.fromStorageBytes(it).timestamp }
                    if (createdVersion != null) {
                        processRecord(key, createdVersion, null)
                    }
                }
            }
            FetchByUniqueKey(firstMatcher.reference)
        }
    }

    val processedScanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs).let {
        if (it is ScanType.TableScan) scanRequest.dataModel.optimizeTableScan(it, keyScanRange) else it
    }

    scanSetup?.invoke(processedScanIndex)

    return when (processedScanIndex) {
        is ScanType.TableScan -> scanStore(
            tableDirs = tableDirs,
            scanRequest = scanRequest,
            direction = processedScanIndex.direction,
            scanRange = keyScanRange,
            processStoreValue = processRecord
        )
        is ScanType.IndexScan ->
            scanIndex(
                tableDirs = tableDirs,
                scanRequest = scanRequest,
                indexScan = processedScanIndex,
                keyScanRange = keyScanRange,
                processStoreValue = processRecord
            )
    }
}

internal fun <DM : IsRootDataModel> shouldProcessRecord(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    createdVersion: ULong?,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges
): Boolean {
    if (createdVersion == null) return false
    val keyBytes = key.bytes
    if (scanRange.keyBeforeStart(keyBytes) || !scanRange.keyWithinRanges(keyBytes, 0) || !scanRange.matchesPartials(keyBytes)) {
        return false
    }
    return !scanRequest.shouldBeFiltered(
        transaction = tr,
        tableDirs = tableDirs,
        key = keyBytes,
        keyOffset = 0,
        keyLength = key.size,
        createdVersion = createdVersion,
        toVersion = scanRequest.toVersion
    )
}
