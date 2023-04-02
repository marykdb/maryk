package maryk.datastore.memory.records.index

import maryk.core.models.IsRootDataModel
import maryk.lib.extensions.compare.compareTo

/**
 * Contains all index values for a specific Indexable and has methods to add, get or remove value references
 */
internal class IndexValues<DM : IsRootDataModel>(
    indexReference: ByteArray
) : AbstractIndexValues<DM, ByteArray>(
    indexReference
) {
    override val compareTo = ByteArray::compareTo
}
