package maryk.datastore.shared.updates

import maryk.core.properties.definitions.index.IsIndexable

/** For passing updates to an index */
sealed class IsIndexUpdate(
    val index: IsIndexable
) {
    class IndexChange(
        index: IsIndexable,
        val indexKey: ByteArray
    ): IsIndexUpdate(index)

    class IndexDelete(
        index: IsIndexable
    ): IsIndexUpdate(index)
}
