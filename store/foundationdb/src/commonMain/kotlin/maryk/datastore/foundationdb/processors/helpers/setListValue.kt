package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.toVarBytes
// Avoid UInt.writeBytes to keep compatibility here; we'll encode manually
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.TypeIndicator

/**
 * Set a full list value for [reference] with [newList] and update count, deleting any tail items beyond new size.
 * Returns true if anything changed.
 */
internal fun <T : Any> setListValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: ListReference<T, *>,
    newList: List<T>,
    originalCount: Int,
    versionBytes: ByteArray
): Boolean {
    val keyAndRef = packKey(tableDirs.tablePrefix, key.bytes, reference.toStorageByteArray())
    @Suppress("UNCHECKED_CAST")
    val valueDef = reference.propertyDefinition.valueDefinition as IsStorageBytesEncodable<T>

    // Update count
    setValue(tr, tableDirs, key.bytes, reference.toStorageByteArray(), versionBytes, newList.size.toVarBytes())

    var changed = false

    // Delete tail items if list shrank
    val toDelete = originalCount - newList.size
    if (toDelete > 0) {
        for (i in 0..toDelete) {
            val refToDelete = keyAndRef.copyOf(keyAndRef.size + 4)
            writeUIntBE(refToDelete, keyAndRef.size, (i + newList.size).toUInt())
            tr.clear(refToDelete)
            if (tableDirs is HistoricTableDirectories) {
                val inv = versionBytes.copyOf().also { it.invert() }
                val qualifier = refToDelete.copyOfRange(packKey(tableDirs.tablePrefix, key.bytes).size, refToDelete.size)
                tr.set(packKey(tableDirs.historicTablePrefix, key.bytes, qualifier, inv), byteArrayOf())
            }
        }
        changed = true
    }

    // Write all new/updated items
    newList.forEachIndexed { idx, item ->
        val itemRef = keyAndRef.copyOf(keyAndRef.size + 4)
        writeUIntBE(itemRef, keyAndRef.size, idx.toUInt())
        val valueBytes = valueDef.toStorageBytes(item, TypeIndicator.NoTypeIndicator.byte)
        tr.set(itemRef, combineToValue(versionBytes, valueBytes))
        if (tableDirs is HistoricTableDirectories) {
            val inv = versionBytes.copyOf().also { it.invert() }
            val qualifier = itemRef.copyOfRange(packKey(tableDirs.tablePrefix, key.bytes).size, itemRef.size)
            tr.set(packKey(tableDirs.historicTablePrefix, key.bytes, qualifier, inv), valueBytes)
        }
        changed = true
    }
    return changed
}

private fun combineToValue(version: ByteArray, value: ByteArray): ByteArray {
    val result = ByteArray(version.size + value.size)
    System.arraycopy(version, 0, result, 0, version.size)
    System.arraycopy(value, 0, result, version.size, value.size)
    return result
}

private fun writeUIntBE(buf: ByteArray, offset: Int, value: UInt) {
    buf[offset] = ((value shr 24) and 0xFFu).toByte()
    buf[offset + 1] = ((value shr 16) and 0xFFu).toByte()
    buf[offset + 2] = ((value shr 8) and 0xFFu).toByte()
    buf[offset + 3] = (value and 0xFFu).toByte()
}
