package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.ValueWriter
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.TRUE
import maryk.datastore.shared.TypeIndicator

internal fun createValueWriter(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    versionBytes: ByteArray,
    qualifiersToKeep: MutableList<ByteArray>? = null,
    currentValues: List<Pair<ByteArray, ByteArray>>? = null,
): ValueWriter<IsPropertyDefinition<*>> = { type, reference, definition, value ->
    when (type) {
        ObjectDelete -> { /* not used here */ }
        Value -> {
            val storable = Value.castDefinition(definition)
            val valueBytes = storable.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
            qualifiersToKeep?.add(reference)
            // If current already contains same qualifier+value, skip write
            val shouldSkip = currentValues?.any { it.first.contentEquals(reference) && it.second.size > VERSION_BYTE_SIZE && it.second.copyOfRange(VERSION_BYTE_SIZE, it.second.size).contentEquals(valueBytes) } == true
            if (!shouldSkip) {
                setValue(tr, tableDirs, key.bytes, reference, versionBytes, valueBytes)
            }
        }
        ListSize, SetSize, MapSize -> {
            val intBytes = (value as Int).toVarBytes()
            qualifiersToKeep?.add(reference)
            val shouldSkip = currentValues?.any { it.first.contentEquals(reference) && it.second.size > VERSION_BYTE_SIZE && it.second.copyOfRange(VERSION_BYTE_SIZE, it.second.size).contentEquals(intBytes) } == true
            if (!shouldSkip) {
                setValue(tr, tableDirs, key.bytes, reference, versionBytes, intBytes)
            }
        }
        TypeValue -> {
            setTypedValue(value, definition, tr, tableDirs, key, reference, versionBytes, qualifiersToKeep)
        }
        Embed -> {
            qualifiersToKeep?.add(reference)
            val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, TRUE)
            setValue(tr, tableDirs, key.bytes, reference, versionBytes, valueBytes)
        }
    }
}
