package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsValuesDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.ValueWriter
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.TRUE
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.isStoredPropertyDeleteMarker
import maryk.core.exceptions.StorageException
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.lib.extensions.compare.matchesRangePart

internal fun FoundationDBDataStore.createValueWriter(
    dataModelId: UInt,
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    versionBytes: ByteArray,
    qualifiersToKeep: MutableList<ByteArray>? = null,
    currentValues: List<Pair<ByteArray, ByteArray>>? = null,
    skipCurrentUniqueOwnership: Boolean = false,
    onUniqueClaim: ((ByteArray, List<ByteArray>) -> Unit)? = null,
    allowUniqueOwnershipRepairScan: Boolean = true,
    onWrite: (() -> Unit)? = null,
): ValueWriter<IsPropertyDefinition<*>> = { type, reference, definition, value ->
    fun shouldSkip(referenceBytes: ByteArray, valueBytes: ByteArray): Boolean =
        currentValues?.any {
            val stored = it.second
            it.first.contentEquals(referenceBytes) &&
                stored.size > VERSION_BYTE_SIZE &&
                stored.size - VERSION_BYTE_SIZE == valueBytes.size &&
                stored.matchesRangePart(
                    fromOffset = VERSION_BYTE_SIZE,
                    bytes = valueBytes,
                    sourceLength = stored.size - VERSION_BYTE_SIZE
                )
        } == true

    when (type) {
        ObjectDelete -> { /* not used here */ }
        Value -> {
            val storable = Value.castDefinition(definition)
            val valueBytes = storable.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
            qualifiersToKeep?.add(reference)
            // If current already contains same qualifier+value, skip write
            if (!shouldSkip(reference, valueBytes)) {
                onWrite?.invoke()
                // Handle unique indexes for comparable unique values on change/writes
                val isComparableUnique = (definition as? IsComparableDefinition<*, *>)?.unique == true

                if (isComparableUnique && !skipCurrentUniqueOwnership) {
                    val uniqueRefs = mapUniqueValueByteCandidates(dataModelId, reference, valueBytes)
                        .map { uniqueValue -> reference + uniqueValue }
                    val uniqueRef = uniqueRefs.first()
                    onUniqueClaim?.invoke(reference, uniqueRefs.map { it.copyOfRange(reference.size, it.size) })
                    try {
                        for (candidateRef in uniqueRefs) {
                            val uniqueExists = tr.get(packKey(tableDirs.uniquePrefix, candidateRef)).awaitResult()
                            if (uniqueExists?.size == VERSION_BYTE_SIZE + key.bytes.size) {
                                val existingKeyBytes = uniqueExists.copyOfRange(
                                    VERSION_BYTE_SIZE,
                                    uniqueExists.size
                                )
                                throw UniqueException(reference, Key<IsValuesDataModel>(existingKeyBytes))
                            }
                        }

                        deleteCurrentUniqueIndexEntryForKey(
                            tr = tr,
                            tableDirs = tableDirs,
                            reference = reference,
                            key = key.bytes,
                            versionBytes = versionBytes,
                            allowRepairScan = allowUniqueOwnershipRepairScan,
                        )

                        createUniqueIndexIfNotExists(dataModelId, tableDirs.uniquePrefix, reference, tr)

                        // Write new unique entry
                        setUniqueIndexValue(tr, tableDirs, uniqueRef, versionBytes, key.bytes)
                    } catch (e: UniqueException) {
                        if (e.key != key) {
                            throw e
                        }
                        // else: same key, ignore
                    }
                }

                val encryptedValue = encryptValueIfSensitive(dataModelId, key.bytes, reference, valueBytes)
                setValue(tr, tableDirs, key.bytes, reference, versionBytes, encryptedValue)
            }
        }
        ListSize, SetSize, MapSize -> {
            val intBytes = (value as Int).toVarBytes()
            qualifiersToKeep?.add(reference)
            if (!shouldSkip(reference, intBytes)) {
                onWrite?.invoke()
                setValue(tr, tableDirs, key.bytes, reference, versionBytes, intBytes)
            }
        }
        TypeValue -> {
            val shouldWrite = if (currentValues == null && onWrite == null) {
                null
            } else {
                { referenceBytes: ByteArray, valueBytes: ByteArray ->
                    val skip = shouldSkip(referenceBytes, valueBytes)
                    if (!skip) onWrite?.invoke()
                    !skip
                }
            }
            setTypedValue(value, definition, tr, tableDirs, key, reference, versionBytes, qualifiersToKeep, shouldWrite)
        }
        Embed -> {
            qualifiersToKeep?.add(reference)
            val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, TRUE)
            if (!shouldSkip(reference, valueBytes)) {
                onWrite?.invoke()
                setValue(tr, tableDirs, key.bytes, reference, versionBytes, valueBytes)
            }
        }
    }
}

private fun FoundationDBDataStore.deleteCurrentUniqueIndexEntryForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    reference: ByteArray,
    key: ByteArray,
    versionBytes: ByteArray,
    allowRepairScan: Boolean,
) {
    val stored = tr.get(packKey(tableDirs.tablePrefix, key, reference)).awaitResult() ?: return
    requireVersionedValue(stored)
    val oldValue = decryptValueIfNeeded(tableDirs.modelId, key, reference, stored,
        VERSION_BYTE_SIZE, stored.size - VERSION_BYTE_SIZE)
    if (isStoredPropertyDeleteMarker(oldValue)) return

    fun clearExact(payload: ByteArray): Boolean {
        var found = false
        for (candidate in mapUniqueValueByteCandidates(tableDirs.modelId, reference, payload)) {
            val uniqueRef = reference + candidate
            val uniqueKey = packKey(tableDirs.uniquePrefix, uniqueRef)
            val owner = tr.get(uniqueKey).awaitResult() ?: continue
            if (owner.size == VERSION_BYTE_SIZE + key.size && owner.matchesRangePart(VERSION_BYTE_SIZE, key)) {
                tr.clear(uniqueKey)
                writeHistoricUnique(tr, tableDirs, key, uniqueRef, versionBytes)
                found = true
            }
        }
        return found
    }
    // Healthy writes never inspect another record's claims.
    if (clearExact(oldValue)) return

    // A damaged current payload can disagree with its still-valid ownership.
    // Prefer its exact original version over scanning the property namespace.
    if (tableDirs is HistoricTableDirectories) {
        val historic = tr.get(packVersionedKey(tableDirs.historicTablePrefix, key,
            encodeZeroFreeUsing01(reference), version = stored.copyOfRange(0, VERSION_BYTE_SIZE))).awaitResult()
        if (historic != null && historic.isNotEmpty()) {
            val source = decryptValueIfNeeded(tableDirs.modelId, key, reference, historic)
            if (clearExact(source)) return
        }
    }
    // Undeletes and version restores can deliberately release the previous claim.
    // Their final reconciliation still validates ownership of the replacement.
    if (!allowRepairScan) return

    // Preserve legacy repair when history is absent/damaged. Bound the work;
    // exceeding this limit aborts the entire mutation, leaving ownership intact.
    val prefix = packKey(tableDirs.uniquePrefix, reference)
    val iterator = tr.getRange(Range.startsWith(prefix), RANGE_SCAN_BATCH_SIZE + 1).iterator()
    try {
        var inspected = 0
        while (iterator.hasNext()) {
            if (++inspected > RANGE_SCAN_BATCH_SIZE) {
                throw StorageException("Unique ownership repair exceeds $RANGE_SCAN_BATCH_SIZE rows; repair the inconsistent property metadata first")
            }
            val row = iterator.nextBlocking()
            if (row.value.size == VERSION_BYTE_SIZE + key.size && row.value.matchesRangePart(VERSION_BYTE_SIZE, key)) {
                tr.clear(row.key)
                writeHistoricUnique(tr, tableDirs, key, row.key.copyOfRange(tableDirs.uniquePrefix.size, row.key.size), versionBytes)
                return
            }
        }
    } finally {
        iterator.cancel()
    }
}
