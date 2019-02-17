package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

/**
 * Contains all unique index values and has methods to add, get or remove unique value references
 */
internal class UniqueIndexValues<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Comparable<T>>(
    indexReference: ByteArray
): AbstractIndexValues<DM, P, T>(
    indexReference
) {
    override val compareTo  = Comparable<T>::compareTo
}
