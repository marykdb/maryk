package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.core.processors.datastore.ValueWriter
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.Bytes
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

/** Replace the complete list subtree so shifted complex items keep their descendants. */
internal fun <T : Any> setListValue(
    values: MutableList<DataRecordNode>,
    reference: TypedPropertyReference<out List<T>>,
    newList: List<T>,
    originalCount: Int,
    version: HLC,
    keepAllVersions: Boolean
): Boolean {
    @Suppress("UNCHECKED_CAST")
    val listReference = reference as ListReference<T, *>
    val prefix = reference.toStorageByteArray()
    val expected = mutableListOf<Pair<ByteArray, Any>>()
    val writer: ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, value -> expected += qualifier to value }
    writeListToStorage(prefix.size, reference::writeStorageBytes, writer,
        listReference.propertyDefinition.definition, newList)
    val expectedReferences = expected.mapTo(hashSetOf()) { Bytes(it.first) }

    var changed = originalCount != newList.size
    val currentReferences = values.asSequence().map { it.reference }
        .filter { it.matchesRangePart(0, prefix) }.toList()
    for (qualifier in currentReferences) {
        if (Bytes(qualifier) !in expectedReferences) {
            val index = values.binarySearch { it.reference compareTo qualifier }
            if (deleteByIndex<Any>(values, index, qualifier, version, keepAllVersions) != null) changed = true
        }
    }
    for ((qualifier, value) in expected) {
        val index = values.binarySearch { it.reference compareTo qualifier }
        if (setValueAtIndex(values, index, qualifier, value, version, keepAllVersions) != null) changed = true
    }
    return changed
}
