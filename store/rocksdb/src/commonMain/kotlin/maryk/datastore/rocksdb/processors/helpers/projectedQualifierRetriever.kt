package maryk.datastore.rocksdb.processors.helpers

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.lib.extensions.compare.compareTo

/** Skip unselected collection subtrees, retaining the ordinary decoder for selected cells. */
internal fun <DM : IsRootDataModel> DM.projectedQualifierRetriever(
    iterator: DBIterator,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
): ((((Int) -> Byte, Int) -> Unit) -> Boolean)? {
    val properties = select?.properties ?: return null
    if (properties.size !in 1..16 || properties.any {
        it !is IsDefinitionWrapper<*, *, *, *> || it.definition !is IsSimpleValueDefinition<*, *>
    }) return null
    // Small scalar records already have an efficient sequential path. Seek only
    // when an unselected complex property could contain many physical cells.
    if (none { property ->
        property.definition !is IsSimpleValueDefinition<*, *> && properties.none { it.index == property.index }
    }) return null
    val targets = (properties.map {
        key.bytes + (it as IsDefinitionWrapper<*, *, *, *>).ref().toStorageByteArray()
    } + listOf(key.bytes + byteArrayOf(SOFT_DELETE_INDICATOR))).sortedWith { a, b -> a.compareTo(b) }
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
