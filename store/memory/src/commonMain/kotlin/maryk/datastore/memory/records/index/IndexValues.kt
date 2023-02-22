package maryk.datastore.memory.records.index

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.lib.extensions.compare.compareTo

/**
 * Contains all index values for a specific Indexable and has methods to add, get or remove value references
 */
internal class IndexValues<DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions>(
    indexReference: ByteArray
) : AbstractIndexValues<DM, P, ByteArray>(
    indexReference
) {
    override val compareTo = ByteArray::compareTo
}
