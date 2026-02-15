package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
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
                val isComparableUnique = try {
                    val comp = (definition as? IsComparableDefinition<*, *>)
                    comp?.unique == true
                } catch (_: Throwable) { false }

                if (isComparableUnique) {
                    val uniqueValue = mapUniqueValueBytes(dataModelId, reference, valueBytes)
                    val uniqueRef = reference + uniqueValue
                    try {
                        val uniqueExists = tr.get(packKey(tableDirs.uniquePrefix, uniqueRef)).awaitResult()
                        if (uniqueExists != null) {
                            val existingKeyBytes = uniqueExists.copyOfRange(
                                VERSION_BYTE_SIZE,
                                uniqueExists.size
                            )
                            throw UniqueException(reference, Key<IsValuesDataModel>(existingKeyBytes))
                        }

                        // Remove old unique entry if present for this qualifier
                        val currentTop = tr.get(packKey(tableDirs.tablePrefix, key.bytes, reference)).awaitResult()
                        if (currentTop != null) {
                            val prevStoredValueBytes = currentTop.copyOfRange(VERSION_BYTE_SIZE, currentTop.size)
                            val prevValueBytes = decryptValueIfNeeded(prevStoredValueBytes)
                            val oldUniqueValue = mapUniqueValueBytes(dataModelId, reference, prevValueBytes)
                            val oldUniqueRef = reference + oldUniqueValue
                            tr.clear(packKey(tableDirs.uniquePrefix, oldUniqueRef))
                        }

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
