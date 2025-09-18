package maryk.datastore.indexeddb.records.index

import maryk.core.models.IsRootDataModel

/**
 * Contains all unique index values and has methods to add, get or remove unique value references
 */
internal class UniqueIndexValues<DM : IsRootDataModel, T : Comparable<T>>(
    indexReference: ByteArray
) : AbstractIndexValues<DM, T>(
    indexReference
) {
    override val compareTo = Comparable<T>::compareTo
}
