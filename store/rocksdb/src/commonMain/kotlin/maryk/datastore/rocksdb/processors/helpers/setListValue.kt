package maryk.datastore.rocksdb.processors.helpers

import maryk.core.processors.datastore.writeListToStorage
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.createValueWriter

/** Rewrite every descendant when list items shift, and remove obsolete descendants. */
internal fun <T : Any> setListValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    reference: ListReference<T, *>,
    newList: List<T>,
    originalCount: Int,
    version: ByteArray
): Boolean {
    val current = getCurrentValues(transaction, columnFamilies, key, reference.toStorageByteArray())
    val keep = mutableListOf<ByteArray>()
    var changed = originalCount != newList.size
    val writer = transaction.rocksDBDataStore.createValueWriter(columnFamilies.modelId, transaction,
        columnFamilies, key, version, qualifiersToKeep = keep,
        skipCurrentUniqueOwnership = true, onWrite = { changed = true })
    writeListToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes,
        writer, reference.propertyDefinition, newList)
    return unsetNonChangedValues(transaction, columnFamilies, key, current, keep, version) || changed
}
