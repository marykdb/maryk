package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.lib.extensions.compare.compareTo

/**
 * Contains all index values for a specific Indexable and has methods to add, get or remove value references
 */
internal class IndexValues<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    indexReference: ByteArray
) : AbstractIndexValues<DM, P, ByteArray>(
    indexReference
) {
    override val compareTo = ByteArray::compareTo
}
