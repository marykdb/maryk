package maryk.datastore.rocksdb.processors.helpers

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.shared.simpleScalarReadProperty
import maryk.lib.extensions.compare.compareTo

/** Skip unselected collection subtrees, retaining the ordinary decoder for selected cells. */
internal fun <DM : IsRootDataModel> DM.prepareProjectedQualifiers(select: RootPropRefGraph<DM>?): List<ByteArray>? {
    val properties = select?.properties ?: return null
    if (properties.size !in 1..16 || properties.any { simpleScalarReadProperty(it) == null }) return null
    // Small scalar records already have an efficient sequential path. Seek only
    // when an unselected complex property could contain many physical cells.
    if (none { property ->
        property.definition !is IsSimpleValueDefinition<*, *> && properties.none { it.index == property.index }
    }) return null
    val qualifiers = ArrayList<ByteArray>(properties.size + 1)
    for (property in properties) {
        qualifiers += (property as IsDefinitionWrapper<*, *, *, *>).ref().toStorageByteArray()
    }
    qualifiers += byteArrayOf(SOFT_DELETE_INDICATOR)
    return qualifiers.sortedWith { a, b -> a.compareTo(b) }
}

/** Qualifiers contain no record keys and can be reused within one request. */
internal fun projectedQualifierRetriever(
    iterator: DBIterator,
    key: Key<*>,
    qualifiers: List<ByteArray>?,
): ((((Int) -> Byte, Int) -> Unit) -> Boolean)? {
    if (qualifiers == null) return null
    val keyBytes = key.bytes
    val targets = qualifiers.map { keyBytes + it }
    var targetIndex = 0
    return findQualifier@{ resultHandler ->
        if (targetIndex >= targets.size) return@findQualifier false
        iterator.next()
        var current = if (iterator.isValid()) iterator.key() else null
        while (targetIndex < targets.size) {
            val target = targets[targetIndex++]
            if (current == null || current.compareTo(target) < 0) {
                iterator.seek(target)
                current = if (iterator.isValid()) iterator.key() else return@findQualifier false
            }
            if (current.contentEquals(target)) {
                val qualifier = current
                resultHandler({ qualifier[key.size + it] }, qualifier.size - key.size)
                return@findQualifier true
            }
        }
        false
    }
}
