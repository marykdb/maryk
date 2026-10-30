package maryk.datastore.foundationdb.processors.helpers

import maryk.core.processors.datastore.writeListToStorage
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.foundationdb.Transaction

/** Rewrite every descendant when list items shift, and remove obsolete descendants. */
internal fun <T : Any> FoundationDBDataStore.setListValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: ListReference<T, *>,
    newList: List<T>,
    originalCount: Int,
    versionBytes: ByteArray
): Boolean {
    val current = getCurrentValuesForPrefix(tr, tableDirs, key, reference.toStorageByteArray())
    val keep = mutableListOf<ByteArray>()
    var changed = originalCount != newList.size
    val writer = createValueWriter(tableDirs.modelId, tr, tableDirs, key, versionBytes,
        qualifiersToKeep = keep, currentValues = current, skipCurrentUniqueOwnership = true,
        onWrite = { changed = true })
    writeListToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes,
        writer, reference.propertyDefinition, newList)
    return unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes) || changed
}
