package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.graph.RootPropRefGraph
import maryk.datastore.shared.scalarReadReference
import maryk.datastore.shared.simpleScalarReadProperty
import maryk.lib.extensions.compare.compareTo

/** Request-local targets for scalar projections over records with unselected collection subtrees. */
internal fun <DM : IsRootDataModel> DM.prepareMemoryProjectedQualifiers(select: RootPropRefGraph<DM>?): List<ByteArray>? {
    val properties = select?.properties ?: return null
    if (properties.size !in 1..16 || properties.any { simpleScalarReadProperty(it) == null }) return null
    if (none { property ->
        property.definition !is IsSimpleValueDefinition<*, *> && properties.none { it.index == property.index }
    }) return null
    val targets = ArrayList<ByteArray>(properties.size + 1)
    // The deletion marker contributes to projected lastVersion even when no value is returned for it.
    targets += objectSoftDeleteQualifier
    for (node in properties) {
        val reference = scalarReadReference(node) ?: return null
        targets += reference.toStorageByteArray()
    }
    return targets.sortedWith { a, b -> a.compareTo(b) }
}

internal class ScanProjection<DM : IsRootDataModel>(
    private val model: DM,
    private val select: RootPropRefGraph<DM>,
) {
    private var prepared = false
    private var targets: List<ByteArray>? = null

    fun qualifiers(recordSize: Int): List<ByteArray>? {
        if (recordSize < 32) return null
        if (!prepared) {
            targets = model.prepareMemoryProjectedQualifiers(select)
            prepared = true
        }
        return targets
    }
}
