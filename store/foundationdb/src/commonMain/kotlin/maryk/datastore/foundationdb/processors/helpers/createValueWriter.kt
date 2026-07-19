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
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.TRUE
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
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

                if (isComparableUnique) {
                    val uniqueRefs = mapUniqueValueByteCandidates(dataModelId, reference, valueBytes)
                        .map { uniqueValue -> reference + uniqueValue }
                    val uniqueRef = uniqueRefs.first()
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
                            versionBytes = versionBytes
                        )

                        createUniqueIndexIfNotExists(dataModelId, tableDirs.uniquePrefix, reference)

                        // Write new unique entry
                        setUniqueIndexValue(tr, tableDirs, uniqueRef, versionBytes, key.bytes)
                    } catch (e: UniqueException) {
                        if (e.key != key) {
                            throw e
                        }
                        // else: same key, ignore
                    }
                }

                val encryptedValue = encryptValueIfSensitive(dataModelId, reference, valueBytes)
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

private fun deleteCurrentUniqueIndexEntryForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    reference: ByteArray,
    key: ByteArray,
    versionBytes: ByteArray
) {
    val prefix = packKey(tableDirs.uniquePrefix, reference)
    val iterator = tr.getRange(Range.startsWith(prefix)).iterator()

    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        if (
            kv.value.size == VERSION_BYTE_SIZE + key.size &&
            kv.value.matchesRangePart(VERSION_BYTE_SIZE, key)
        ) {
            tr.clear(kv.key)
            val uniqueRef = kv.key.copyOfRange(tableDirs.uniquePrefix.size, kv.key.size)
            writeHistoricUnique(tr, tableDirs, key, uniqueRef, versionBytes)
            break
        }
    }
}
