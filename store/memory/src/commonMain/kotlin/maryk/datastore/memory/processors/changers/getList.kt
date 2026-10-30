package maryk.datastore.memory.processors.changers

import maryk.core.processors.datastore.readStorageToList
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.datastore.memory.records.DataRecordNode
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

/** Read list items together with their embedded or typed descendants. */
internal fun <T : Any> getList(
    values: List<DataRecordNode>,
    reference: TypedPropertyReference<out List<T>>
): MutableList<T>? {
    val prefix = reference.toStorageByteArray()
    var index = values.binarySearch { it.reference compareTo prefix }
    if (index < 0) return null
    var valueIndex = index
    @Suppress("UNCHECKED_CAST")
    return readStorageToList(
        reference as ListReference<T, *>,
        getQualifier = { handler ->
            val qualifier = values.getOrNull(index)?.reference
            if (qualifier != null && qualifier.matchesRangePart(0, prefix)) {
                valueIndex = index++
                handler({ qualifier[it] }, qualifier.size)
                true
            } else false
        },
        processValue = { _, _ -> getValueAtIndex<Any>(values, valueIndex)?.value },
    )
}
