package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Transaction
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
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.getKeyByUniqueValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfPresent
import maryk.datastore.foundationdb.processors.helpers.TransactionRunner
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType

internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScan(
    scanRequest: IsScanRequest<DM, *>,
    tableDirs: IsTableDirectories,
    scanSetup: ((ScanType) -> Unit)? = null,
    processRecord: (Transaction, Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val transactionRunner = object : TransactionRunner {
        override fun <T> run(block: (Transaction) -> T): T =
            runTransaction { tr ->
                block(tr)
            }
    }
    val dataModelId = getDataModelId(scanRequest.dataModel)
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(keepAllVersions)

    if (keyScanRange.ranges.isEmpty()) {
        return FetchByTableScan(ASC, null, null)
    }

    // If hard key match then quit with direct record
    if (keyScanRange.isSingleKey()) {
        val key = scanRequest.dataModel.key(keyScanRange.ranges.first().start)
        transactionRunner.run { tr ->
            val exists = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
            if (exists != null) {
                val createdVersion = exists.readHLCTimestampIfPresent()
                    ?: return@run
                if (shouldProcessRecord(tr, tableDirs, key, createdVersion, scanRequest, keyScanRange, this::decryptValueIfNeeded)) {
                    processRecord(tr, key, createdVersion, null)
                }
            }
        }
        return FetchByKey
    }

    // Fast path for unique lookups: resolve unique -> key directly (supports toVersion).
    keyScanRange.uniques?.takeIf { it.isNotEmpty() }?.let { uniqueMatchers ->
        val firstMatcher = uniqueMatchers.first()
        @Suppress("UNCHECKED_CAST")
        val valueBytes = (firstMatcher.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>)
            .toStorageBytes(firstMatcher.value as Comparable<Any>)
        val rawUniqueValue = ByteArray(1 + valueBytes.size).apply {
            this[0] = TypeIndicator.NoTypeIndicator.byte
            valueBytes.copyInto(this, 1)
        }
        val uniqueValue = mapUniqueValueBytes(dataModelId, firstMatcher.reference, rawUniqueValue)

        // Build (reference || uniqueValue) to match how uniques are stored
        val reference = ByteArray(firstMatcher.reference.size + uniqueValue.size).apply {
            firstMatcher.reference.copyInto(this)
            uniqueValue.copyInto(this, firstMatcher.reference.size)
        }

        transactionRunner.run { tr ->
            tr.getKeyByUniqueValue(tableDirs, reference, scanRequest.toVersion) { keyBytes, keyOffset, keyLength, setAtVersion ->
                var keyReadIndex = keyOffset
                val key = scanRequest.dataModel.key {
                    keyBytes[keyReadIndex++]
                }
                if (shouldProcessRecord(tr, tableDirs, key, setAtVersion, scanRequest, keyScanRange, this::decryptValueIfNeeded)) {
                    // Ensure we have the creation version for processRecord callback
                    val createdBytes = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
                    val createdVersion = createdBytes?.readHLCTimestampIfPresent()
                    if (createdVersion != null) {
                        processRecord(tr, key, createdVersion, null)
                    }
                }
            }
        }

        return FetchByUniqueKey(firstMatcher.reference)
    }

    val processedScanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs).let {
        if (it is ScanType.TableScan) {
            scanRequest.dataModel.optimizeTableScan(
                it,
                keyScanRange,
                filter = scanRequest.where,
                allowTableScan = scanRequest.allowTableScan
            )
        } else it
    }

    scanSetup?.invoke(processedScanIndex)

    return when (processedScanIndex) {
        is ScanType.TableScan -> scanStore(
            transactionRunner = transactionRunner,
            tableDirs = tableDirs,
            scanRequest = scanRequest,
            direction = processedScanIndex.direction,
            scanRange = keyScanRange,
            decryptValue = this::decryptValueIfNeeded,
            processStoreValue = processRecord
        )
        is ScanType.IndexScan ->
            scanIndex(
                transactionRunner = transactionRunner,
                tableDirs = tableDirs,
                scanRequest = scanRequest,
                indexScan = processedScanIndex,
                keyScanRange = keyScanRange,
                decryptValue = this::decryptValueIfNeeded,
                processStoreValue = processRecord
            )
        is ScanType.UpdateHistoryScan -> throw IllegalStateException("UpdateHistoryScan is only supported by scanUpdates")
    }
}

internal fun <DM : IsRootDataModel> shouldProcessRecord(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    createdVersion: ULong?,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges,
    decryptValue: DecryptValue? = null
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
        toVersion = scanRequest.toVersion,
        decryptValue = decryptValue
    )
}
