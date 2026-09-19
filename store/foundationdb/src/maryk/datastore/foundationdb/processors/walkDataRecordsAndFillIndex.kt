package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.values.IsValuesGetter
import maryk.core.values.IsStreamingValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.model.modelIndexRebuildScratchKey
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01OrNull
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.isHistoricDeleteMarker
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.withCurrentPayload
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.shared.readValue
import maryk.datastore.shared.rethrowIfFatal
import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext

private val scratchEventsMarker = byteArrayOf(0)
private val scratchStateMarker = byteArrayOf(1)
private val scratchOutputMarker = byteArrayOf(2)

internal const val INDEX_REBUILD_ROWS_PER_READ_TRANSACTION = 32
internal const val INDEX_REBUILD_BYTES_PER_READ_TRANSACTION = 64 * 1024
internal const val INDEX_REBUILD_MUTATIONS_PER_WRITE_TRANSACTION = 16

private data class RawStorageRow(
    val key: ByteArray,
    val value: ByteArray,
)

private data class RebuildSnapshot(val values: IsValuesGetter, val isDeleted: Boolean)

internal data class IndexRebuildReadTransaction(
    val rows: Int,
    val bytes: Int,
)

/** Emitted after a bounded index-mutation transaction has committed. */
internal data class IndexRebuildWriteTransaction(
    val mutations: Int,
    val bytes: Int,
)

private sealed interface IndexMutation {
    data class Current(
        val indexReference: ByteArray,
        val valueAndKey: ByteArray,
        val version: ByteArray,
    ) : IndexMutation

    data class Historic(
        val indexReference: ByteArray,
        val valueAndKey: ByteArray,
        val version: ByteArray,
        val marker: ByteArray,
    ) : IndexMutation
}

/**
 * Rebuild indexes in bounded FoundationDB transactions.
 *
 * A record's storage rows are read in short transactions and decoded after the transaction closes.
 * This is deliberate: arbitrary index graphs can require the materialized record, but no
 * FoundationDB read or write transaction grows with that record or its complete history.
 */
internal fun walkDataRecordsAndFillIndex(
    tc: TransactionContext,
    tableDirectories: IsTableDirectories,
    indexesToIndex: List<IsIndexable>,
    dataModel: IsRootDataModel,
    decryptValue: DecryptValue? = null,
    rowsPerReadTransaction: Int = INDEX_REBUILD_ROWS_PER_READ_TRANSACTION,
    bytesPerReadTransaction: Int = INDEX_REBUILD_BYTES_PER_READ_TRANSACTION,
    mutationsPerWriteTransaction: Int = INDEX_REBUILD_MUTATIONS_PER_WRITE_TRANSACTION,
    bytesPerWriteTransaction: Int = INDEX_REBUILD_BYTES_PER_READ_TRANSACTION,
    historicVersionsPerTransaction: Int = INDEX_REBUILD_MUTATIONS_PER_WRITE_TRANSACTION,
    historicCollectionEntriesPerTransaction: Int = INDEX_REBUILD_MUTATIONS_PER_WRITE_TRANSACTION,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)? = null,
    onWriteTransaction: ((IndexRebuildWriteTransaction) -> Unit)? = null,
    onRecordMaterialized: ((ByteArray) -> Unit)? = null,
    verifyRebuildOwner: ((Transaction) -> Unit)? = null,
    historicScratchPrefix: ByteArray? = null,
): Int {
    if (indexesToIndex.isEmpty()) return 0
    require(rowsPerReadTransaction > 0) { "rowsPerReadTransaction must be positive" }
    require(bytesPerReadTransaction > 0) { "bytesPerReadTransaction must be positive" }
    require(mutationsPerWriteTransaction > 0) { "mutationsPerWriteTransaction must be positive" }
    require(bytesPerWriteTransaction > 0) { "bytesPerWriteTransaction must be positive" }
    require(historicVersionsPerTransaction > 0) { "historicVersionsPerTransaction must be positive" }
    require(historicCollectionEntriesPerTransaction > 0) { "historicCollectionEntriesPerTransaction must be positive" }
    val writeBatchSize = minOf(
        mutationsPerWriteTransaction,
        historicVersionsPerTransaction,
        historicCollectionEntriesPerTransaction,
    )
    val effectiveHistoricScratchPrefix = when {
        tableDirectories !is HistoricTableDirectories -> null
        historicScratchPrefix != null -> historicScratchPrefix
        else -> packKey(tableDirectories.modelPrefix, modelIndexRebuildScratchKey)
    }
    if (effectiveHistoricScratchPrefix != null && historicScratchPrefix == null) {
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            transaction.clear(Range.startsWith(effectiveHistoricScratchPrefix))
        }
    }

    var nextKey: ByteArray? = null
    var writeTransactions = 0

    while (true) {
        val keyRow = readNextKeyRow(tc, tableDirectories.keysPrefix, nextKey) ?: break
        nextKey = keyRow.key
        if (keyRow.value.readHLCTimestampIfExact() == null) continue

        val keyBytes = keyRow.key.copyOfRange(tableDirectories.keysPrefix.size, keyRow.key.size)
        val sink = IndexMutationSink(
            tc,
            tableDirectories,
            writeBatchSize,
            bytesPerWriteTransaction,
            verifyRebuildOwner,
            onWriteTransaction,
        )
        val materialized = materializeRecord(
            tc,
            tableDirectories,
            indexesToIndex,
            dataModel,
            keyBytes,
            rowsPerReadTransaction,
            bytesPerReadTransaction,
            decryptValue,
            onReadTransaction,
            sink,
            effectiveHistoricScratchPrefix,
            verifyRebuildOwner,
            writeBatchSize,
            bytesPerWriteTransaction,
        )
        if (!materialized) continue
        onRecordMaterialized?.invoke(keyBytes)
        writeTransactions += sink.flush()
    }

    if (effectiveHistoricScratchPrefix != null && historicScratchPrefix == null) {
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            transaction.clear(Range.startsWith(effectiveHistoricScratchPrefix))
        }
    }
    return writeTransactions
}

private fun materializeRecord(
    tc: TransactionContext,
    tableDirectories: IsTableDirectories,
    indexesToIndex: List<IsIndexable>,
    dataModel: IsRootDataModel,
    keyBytes: ByteArray,
    rowsPerReadTransaction: Int,
    bytesPerReadTransaction: Int,
    decryptValue: DecryptValue?,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
    sink: IndexMutationSink,
    historicScratchPrefix: ByteArray?,
    verifyRebuildOwner: ((Transaction) -> Unit)?,
    scratchMutationsPerTransaction: Int,
    scratchBytesPerTransaction: Int,
): Boolean {
    val recordPrefix = packKey(tableDirectories.tablePrefix, keyBytes)
    val latestVersion = readCurrentLatestVersion(tc, recordPrefix, onReadTransaction)
    if (latestVersion == null && tableDirectories !is HistoricTableDirectories) return false
    val currentValues = RebuildValuesGetter.current(
        tc, recordPrefix, keyBytes, tableDirectories.modelId, decryptValue, rowsPerReadTransaction, bytesPerReadTransaction, onReadTransaction,
    )
    val currentSnapshot = RebuildSnapshot(currentValues, currentValues.isDeleted())
    if (tableDirectories is HistoricTableDirectories && historicScratchPrefix != null) {
        transposeHistoricRowsToScratch(
            tc, tableDirectories, keyBytes, rowsPerReadTransaction, bytesPerReadTransaction,
            historicScratchPrefix, verifyRebuildOwner, onReadTransaction,
        )
    }
    indexesToIndex.forEach { indexable ->
        val indexReference = indexable.referenceStorageByteArray.bytes
        latestVersion?.let { currentVersion -> currentSnapshot.takeUnless { it.isDeleted }?.let { snapshot ->
            snapshot.forEachIndexValue(indexable, keyBytes) { value ->
                sink.add(IndexMutation.Current(indexReference, value, currentVersion))
            }
        } }
        if (tableDirectories is HistoricTableDirectories) {
            replayHistoricIndexThroughScratch(
                tc, indexable, indexReference, keyBytes, tableDirectories.modelId,
                rowsPerReadTransaction, bytesPerReadTransaction, decryptValue,
                requireNotNull(historicScratchPrefix), sink, verifyRebuildOwner, onReadTransaction,
                scratchMutationsPerTransaction, scratchBytesPerTransaction,
            )
        }
    }
    return latestVersion != null || tableDirectories is HistoricTableDirectories
}

private fun readNextKeyRow(
    tc: TransactionContext,
    keysPrefix: ByteArray,
    afterKey: ByteArray?,
): KeyValue? = tc.run { tr ->
    tr.getRange(Range(afterKey ?: keysPrefix, Range.startsWith(keysPrefix).end), 2, false)
        .asList()
        .awaitResult()
        .firstOrNull { afterKey == null || !it.key.contentEquals(afterKey) }
}

private fun forEachRawStoragePage(
    tc: TransactionContext,
    range: Range,
    rowsPerTransaction: Int,
    bytesPerTransaction: Int,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
    process: (List<RawStorageRow>) -> Unit,
) {
    var afterKey: ByteArray? = null

    while (true) {
        val page = tc.run { tr ->
            val pageRows = mutableListOf<RawStorageRow>()
            var nextStart = afterKey?.plus(byteArrayOf(0)) ?: range.begin
            var bytes = 0
            while (pageRows.size < rowsPerTransaction) {
                val row = tr.getRange(Range(nextStart, range.end), 1, false)
                    .asList()
                    .awaitResult()
                    .firstOrNull()
                    ?: break
                val rowBytes = row.key.size + row.value.size
                // A legal single FDB row can exceed the caller budget. It is
                // still bounded to one row/transaction; only further rows are
                // deferred to the next transaction.
                if (pageRows.isNotEmpty() && bytes + rowBytes > bytesPerTransaction) break
                pageRows += RawStorageRow(row.key.copyOf(), row.value.copyOf())
                bytes += rowBytes
                nextStart = row.key + byteArrayOf(0)
            }
            pageRows
        }
        if (page.isEmpty()) break
        onReadTransaction?.invoke(IndexRebuildReadTransaction(
            rows = page.size,
            bytes = page.sumOf { it.key.size + it.value.size },
        ))
        process(page)
        afterKey = page.last().key
    }
}

private fun decodeValue(
    reference: IsPropertyReferenceForCache<*, *>,
    payload: ByteArray,
    offset: Int,
    length: Int,
): Any? {
    var index = offset
    val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
        ?: reference.propertyDefinition
    return readValue(definition, { payload[index++] }) { offset + length - index }
}

private fun readCurrentLatestVersion(
    tc: TransactionContext,
    recordPrefix: ByteArray,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
): ByteArray? {
    val value = tc.run { transaction -> transaction.get(recordPrefix).awaitResult()?.copyOf() }
    onReadTransaction?.invoke(IndexRebuildReadTransaction(if (value == null) 0 else 1, recordPrefix.size + (value?.size ?: 0)))
    return value?.takeIf { it.readHLCTimestampIfExact() != null }
}

/**
 * Read only the scalar a particular index needs. Collection `Any` references
 * enumerate their raw leaves page by page, so repeated composite evaluation
 * retains only its active branch rather than a record-sized Map or Set.
 */
private class RebuildValuesGetter private constructor(
    private val tc: TransactionContext,
    private val valuePrefix: ByteArray,
    private val keyBytes: ByteArray,
    private val modelId: UInt,
    private val historicState: Boolean,
    private val decryptValue: DecryptValue?,
    private val rowsPerTransaction: Int,
    private val bytesPerTransaction: Int,
    private val onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
) : IsStreamingValuesGetter {
    companion object {
        fun current(
            tc: TransactionContext,
            recordPrefix: ByteArray,
            keyBytes: ByteArray,
            modelId: UInt,
            decryptValue: DecryptValue?,
            rowsPerTransaction: Int,
            bytesPerTransaction: Int,
            onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
        ) = RebuildValuesGetter(
            tc, recordPrefix, keyBytes, modelId, false, decryptValue, rowsPerTransaction, bytesPerTransaction, onReadTransaction,
        )

        fun historicState(
            tc: TransactionContext,
            statePrefix: ByteArray,
            keyBytes: ByteArray,
            modelId: UInt,
            decryptValue: DecryptValue?,
            rowsPerTransaction: Int,
            bytesPerTransaction: Int,
            onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
        ) = RebuildValuesGetter(
            tc, statePrefix, keyBytes, modelId, true, decryptValue, rowsPerTransaction, bytesPerTransaction, onReadTransaction,
        )
    }

    override fun <T : Any, D : maryk.core.properties.definitions.IsPropertyDefinition<T>, C : Any> get(
        propertyReference: maryk.core.properties.references.IsPropertyReference<T, D, C>,
    ): T? {
        val stored = readExact(propertyReference.toStorageByteArray()) ?: return null
        return try {
            payload(stored, propertyReference.toStorageByteArray()) { bytes, offset, length ->
                @Suppress("UNCHECKED_CAST")
                decodeValue(propertyReference, bytes, offset, length) as? T
            }
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            null
        }
    }

    override fun streamMapKeys(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean {
        @Suppress("UNCHECKED_CAST")
        val mapDefinition = parent.unwrappedDefinition() as? IsMapDefinition<Any, Any, IsPropertyContext>
            ?: return false
        val parentBytes = parent.toStorageByteArray()
        forEachQualifierStartingWith(parentBytes) { qualifier, _ ->
            val key = try {
                var index = parentBytes.size
                val keyLength = initIntByVar { qualifier[index++] }
                val key = mapDefinition.keyDefinition.readStorageBytes(keyLength) {
                    qualifier[index++]
                }
                key.takeIf { index == qualifier.size }
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                null
            }
            key?.let(emit)
        }
        return true
    }

    override fun streamSetValues(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean {
        @Suppress("UNCHECKED_CAST")
        val setDefinition = parent.unwrappedDefinition() as? IsSetDefinition<Any, IsPropertyContext>
            ?: return false
        val parentBytes = parent.toStorageByteArray()
        forEachQualifierStartingWith(parentBytes) { qualifier, _ ->
            val value = try {
                var index = parentBytes.size
                val valueLength = initIntByVar { qualifier[index++] }
                @Suppress("UNCHECKED_CAST")
                val definition = setDefinition.valueDefinition
                    as maryk.core.properties.definitions.IsStorageBytesEncodable<Any>
                val value = definition.readStorageBytes(valueLength) { qualifier[index++] }
                value.takeIf { index == qualifier.size }
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                null
            }
            value?.let(emit)
        }
        return true
    }

    fun isDeleted(): Boolean = readExact(byteArrayOf(SOFT_DELETE_INDICATOR))?.let { stored ->
        payload(stored, byteArrayOf(SOFT_DELETE_INDICATOR)) { bytes, offset, length -> length == 1 && bytes[offset] == TRUE }
    } == true

    private fun readExact(qualifier: ByteArray): ByteArray? {
        val key = if (historicState) {
            packKey(valuePrefix, encodeZeroFreeUsing01(qualifier))
        } else {
            packKey(valuePrefix, qualifier)
        }
        val value = tc.run { transaction -> transaction.get(key).awaitResult()?.copyOf() }
        onReadTransaction?.invoke(IndexRebuildReadTransaction(if (value == null) 0 else 1, key.size + (value?.size ?: 0)))
        return value
    }

    private fun forEachQualifierStartingWith(
        qualifierPrefix: ByteArray,
        process: (ByteArray, ByteArray) -> Unit,
    ) {
        val keyPrefix = if (historicState) {
            packKey(valuePrefix, encodeZeroFreeUsing01(qualifierPrefix))
        } else {
            packKey(valuePrefix, qualifierPrefix)
        }
        forEachRawStoragePage(
            tc, Range.startsWith(keyPrefix), rowsPerTransaction, bytesPerTransaction, onReadTransaction,
        ) { page ->
            page.forEach { row ->
                val qualifier = if (historicState) {
                    decodeZeroFreeUsing01OrNull(row.key, valuePrefix.size, row.key.size - valuePrefix.size)
                        ?: return@forEach
                } else {
                    row.key.copyOfRange(valuePrefix.size, row.key.size)
                }
                if (qualifier.size >= qualifierPrefix.size && qualifier.copyOfRange(0, qualifierPrefix.size).contentEquals(qualifierPrefix)) {
                    process(qualifier, row.value)
                }
            }
        }
    }

    private inline fun <T> payload(stored: ByteArray, qualifier: ByteArray, process: (ByteArray, Int, Int) -> T): T {
        return if (historicState) {
            val payload = decryptValue?.invoke(modelId, stored, 0, stored.size, keyBytes, qualifier) ?: stored
            process(payload, 0, payload.size)
        } else {
            stored.withCurrentPayload(decryptValue, modelId, keyBytes, qualifier, process)
        }
    }
}

private fun AnyPropertyReference.unwrappedDefinition() =
    (propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition ?: propertyDefinition

private fun RebuildSnapshot.forEachIndexValue(
    indexable: IsIndexable,
    keyBytes: ByteArray,
    emit: (ByteArray) -> Unit,
) {
    indexable.forEachStorageByteArrayForIndex(values, keyBytes, emit)
}

private fun transposeHistoricRowsToScratch(
    tc: TransactionContext,
    tableDirectories: HistoricTableDirectories,
    keyBytes: ByteArray,
    rowsPerTransaction: Int,
    bytesPerTransaction: Int,
    scratchPrefix: ByteArray,
    verifyRebuildOwner: ((Transaction) -> Unit)?,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
) {
    val historicPrefix = packKey(tableDirectories.historicTablePrefix, keyBytes)
    val eventPrefix = scratchEventPrefix(scratchPrefix, keyBytes)
    forEachRawStoragePage(
        tc, Range.startsWith(historicPrefix), rowsPerTransaction, bytesPerTransaction, onReadTransaction,
    ) { page ->
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            page.forEach { row ->
                val qualifierBytes = row.key.copyOfRange(historicPrefix.size, row.key.size)
                if (qualifierBytes.size <= VERSION_BYTE_SIZE + 1) return@forEach
                val versionOffset = qualifierBytes.size - VERSION_BYTE_SIZE
                val separator = versionOffset - 1
                if (qualifierBytes[separator] != 0.toByte()) return@forEach
                val qualifier = decodeZeroFreeUsing01OrNull(qualifierBytes, 0, separator) ?: return@forEach
                val version = HLC.toStorageBytes(HLC(qualifierBytes.readReversedVersionBytes(versionOffset)))
                transaction.set(
                    packKey(eventPrefix, version, encodeZeroFreeUsing01(qualifier)),
                    row.value,
                )
            }
        }
    }
}

private fun replayHistoricIndexThroughScratch(
    tc: TransactionContext,
    indexable: IsIndexable,
    indexReference: ByteArray,
    keyBytes: ByteArray,
    modelId: UInt,
    rowsPerTransaction: Int,
    bytesPerTransaction: Int,
    decryptValue: DecryptValue?,
    scratchPrefix: ByteArray,
    mutationSink: IndexMutationSink,
    verifyRebuildOwner: ((Transaction) -> Unit)?,
    onReadTransaction: ((IndexRebuildReadTransaction) -> Unit)?,
    scratchMutationsPerTransaction: Int,
    scratchBytesPerTransaction: Int,
) {
    val eventPrefix = scratchEventPrefix(scratchPrefix, keyBytes)
    val statePrefix = scratchStatePrefix(scratchPrefix, indexReference, keyBytes)
    val outputPrefixes = arrayOf(
        scratchOutputPrefix(scratchPrefix, 0, indexReference, keyBytes),
        scratchOutputPrefix(scratchPrefix, 1, indexReference, keyBytes),
    )
    var previousOutput = 0
    var currentVersion: ByteArray? = null

    fun flushVersion(version: ByteArray) {
        val values = RebuildValuesGetter.historicState(
            tc, statePrefix, keyBytes, modelId, decryptValue, rowsPerTransaction, bytesPerTransaction, onReadTransaction,
        )
        val snapshot = RebuildSnapshot(values, values.isDeleted())
        val nextOutput = 1 - previousOutput
        val outputPrefix = outputPrefixes[nextOutput]
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            transaction.clear(Range.startsWith(outputPrefix))
        }
        val outputSink = ScratchOutputSink(
            tc, outputPrefix, scratchMutationsPerTransaction, scratchBytesPerTransaction, verifyRebuildOwner,
        )
        snapshot.takeUnless { it.isDeleted }?.forEachIndexValue(indexable, keyBytes) { value ->
            outputSink.add(value)
        }
        outputSink.flush()
        diffScratchOutputs(
            tc, outputPrefixes[previousOutput], outputPrefix, indexReference,
            version, mutationSink,
        )
        previousOutput = nextOutput
    }

    forEachRawStoragePage(
        tc, Range.startsWith(eventPrefix), rowsPerTransaction, bytesPerTransaction, onReadTransaction,
    ) { page ->
        page.forEach { row ->
            val versionOffset = eventPrefix.size
            val version = row.key.copyOfRange(versionOffset, versionOffset + VERSION_BYTE_SIZE)
            if (currentVersion != null && !currentVersion!!.contentEquals(version)) {
                flushVersion(currentVersion!!)
            }
            currentVersion = version
            val encodedQualifier = row.key.copyOfRange(versionOffset + VERSION_BYTE_SIZE, row.key.size)
            val qualifier = decodeZeroFreeUsing01OrNull(encodedQualifier) ?: return@forEach
            val stateKey = packKey(statePrefix, encodeZeroFreeUsing01(qualifier))
            tc.run { transaction ->
                verifyRebuildOwner?.invoke(transaction)
                if (row.value.isHistoricDeleteMarker()) transaction.clear(stateKey) else transaction.set(stateKey, row.value)
            }
        }
    }
    currentVersion?.let(::flushVersion)
}

private class ScratchOutputSink(
    private val tc: TransactionContext,
    private val outputPrefix: ByteArray,
    private val mutationsPerTransaction: Int,
    private val byteBudget: Int,
    private val verifyRebuildOwner: ((Transaction) -> Unit)?,
) {
    private val pending = mutableListOf<ByteArray>()
    private var pendingBytes = 0

    fun add(value: ByteArray) {
        if (pending.isNotEmpty() && (pending.size >= mutationsPerTransaction || pendingBytes + value.size > byteBudget)) flush()
        pending += value.copyOf()
        pendingBytes += value.size
    }

    fun flush() {
        if (pending.isEmpty()) return
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            pending.forEach { value -> transaction.set(packKey(outputPrefix, encodeZeroFreeUsing01(value)), EMPTY_BYTEARRAY) }
        }
        pending.clear()
        pendingBytes = 0
    }
}

private fun diffScratchOutputs(
    tc: TransactionContext,
    previousPrefix: ByteArray,
    currentPrefix: ByteArray,
    indexReference: ByteArray,
    version: ByteArray,
    sink: IndexMutationSink,
) {
    var previous: RawStorageRow? = null
    var current: RawStorageRow? = null
    var previousAfter: ByteArray? = null
    var currentAfter: ByteArray? = null
    while (true) {
        if (previous == null) previous = readNextScratchOutput(tc, previousPrefix, previousAfter)
        if (current == null) current = readNextScratchOutput(tc, currentPrefix, currentAfter)
        val old = previous
        val next = current
        if (old == null && next == null) return
        when {
            old == null -> {
                sink.add(IndexMutation.Historic(indexReference, decodeScratchOutput(currentPrefix, next!!), version, EMPTY_BYTEARRAY))
                currentAfter = next.key
                current = null
            }
            next == null -> {
                sink.add(IndexMutation.Historic(indexReference, decodeScratchOutput(previousPrefix, old), version, HISTORIC_REMOVAL_MARKER))
                previousAfter = old.key
                previous = null
            }
            compareUnsigned(old.key, previousPrefix.size, next.key, currentPrefix.size) < 0 -> {
                sink.add(IndexMutation.Historic(indexReference, decodeScratchOutput(previousPrefix, old), version, HISTORIC_REMOVAL_MARKER))
                previousAfter = old.key
                previous = null
            }
            compareUnsigned(old.key, previousPrefix.size, next.key, currentPrefix.size) > 0 -> {
                sink.add(IndexMutation.Historic(indexReference, decodeScratchOutput(currentPrefix, next), version, EMPTY_BYTEARRAY))
                currentAfter = next.key
                current = null
            }
            else -> {
                previousAfter = old.key
                currentAfter = next.key
                previous = null
                current = null
            }
        }
    }
}

private fun readNextScratchOutput(tc: TransactionContext, prefix: ByteArray, after: ByteArray?): RawStorageRow? = tc.run { transaction ->
    transaction.getRange(Range(after?.plus(byteArrayOf(0)) ?: prefix, Range.startsWith(prefix).end), 1, false)
        .asList().awaitResult().firstOrNull()?.let { RawStorageRow(it.key.copyOf(), it.value.copyOf()) }
}

private fun decodeScratchOutput(prefix: ByteArray, row: RawStorageRow): ByteArray =
    decodeZeroFreeUsing01OrNull(row.key, prefix.size, row.key.size - prefix.size)
        ?: error("Invalid index rebuild scratch output")

private fun compareUnsigned(first: ByteArray, firstOffset: Int, second: ByteArray, secondOffset: Int): Int {
    var firstIndex = firstOffset
    var secondIndex = secondOffset
    while (firstIndex < first.size && secondIndex < second.size) {
        val comparison = first[firstIndex++].toUByte().compareTo(second[secondIndex++].toUByte())
        if (comparison != 0) return comparison
    }
    return (first.size - firstIndex).compareTo(second.size - secondIndex)
}

private fun scratchEventPrefix(scratchPrefix: ByteArray, keyBytes: ByteArray) =
    packKey(scratchPrefix, scratchEventsMarker, encodeZeroFreeUsing01(keyBytes), byteArrayOf(0))

private fun scratchStatePrefix(scratchPrefix: ByteArray, indexReference: ByteArray, keyBytes: ByteArray) =
    packKey(
        scratchPrefix, scratchStateMarker, encodeZeroFreeUsing01(indexReference), byteArrayOf(0),
        encodeZeroFreeUsing01(keyBytes), byteArrayOf(0),
    )

private fun scratchOutputPrefix(scratchPrefix: ByteArray, slot: Int, indexReference: ByteArray, keyBytes: ByteArray) =
    packKey(
        scratchPrefix, scratchOutputMarker, byteArrayOf(slot.toByte()),
        encodeZeroFreeUsing01(indexReference), byteArrayOf(0),
        encodeZeroFreeUsing01(keyBytes), byteArrayOf(0),
    )

private class IndexMutationSink(
    private val tc: TransactionContext,
    private val tableDirectories: IsTableDirectories,
    private val mutationsPerTransaction: Int,
    private val byteBudget: Int,
    private val verifyRebuildOwner: ((Transaction) -> Unit)?,
    private val onWriteTransaction: ((IndexRebuildWriteTransaction) -> Unit)?,
) {
    private val pending = mutableListOf<IndexMutation>()
    private var pendingBytes = 0
    private var transactions = 0

    fun add(mutation: IndexMutation) {
        val mutationBytes = mutation.estimatedSize()
        if (pending.isNotEmpty() && (pending.size >= mutationsPerTransaction || pendingBytes + mutationBytes > byteBudget)) {
            flush()
        }
        pending += mutation
        pendingBytes += mutationBytes
    }

    fun flush(): Int {
        if (pending.isEmpty()) return transactions
        tc.run { transaction ->
            verifyRebuildOwner?.invoke(transaction)
            pending.forEach { mutation -> transaction.writeIndexMutation(tableDirectories, mutation) }
        }
        onWriteTransaction?.invoke(IndexRebuildWriteTransaction(pending.size, pendingBytes))
        transactions++
        pending.clear()
        pendingBytes = 0
        return transactions
    }
}

private fun IndexMutation.estimatedSize() = when (this) {
    is IndexMutation.Current -> indexReference.size + valueAndKey.size + version.size
    is IndexMutation.Historic -> indexReference.size + valueAndKey.size + version.size + marker.size
}

private fun Transaction.writeIndexMutation(tableDirectories: IsTableDirectories, mutation: IndexMutation) = when (mutation) {
    is IndexMutation.Current -> set(
        packKey(tableDirectories.indexPrefix, mutation.indexReference, mutation.valueAndKey),
        mutation.version,
    )
    is IndexMutation.Historic -> writeHistoricIndex(
        this,
        tableDirectories,
        mutation.indexReference,
        mutation.valueAndKey,
        mutation.version,
        mutation.marker,
    )
}

private fun Transaction.clearIndexMutation(tableDirectories: IsTableDirectories, mutation: IndexMutation) = when (mutation) {
    is IndexMutation.Current -> clear(packKey(tableDirectories.indexPrefix, mutation.indexReference, mutation.valueAndKey))
    is IndexMutation.Historic -> if (tableDirectories is HistoricTableDirectories) {
        val encodedQualifier = encodeZeroFreeUsing01(mutation.indexReference, mutation.valueAndKey)
        clear(packVersionedKey(tableDirectories.historicIndexPrefix, encodedQualifier, version = mutation.version))
    } else Unit
}
