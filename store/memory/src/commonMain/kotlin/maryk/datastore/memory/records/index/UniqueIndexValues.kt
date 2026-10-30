package maryk.datastore.memory.records.index

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.numeric.NumberType
import maryk.datastore.memory.records.compareUniqueValues

/**
 * Contains all unique index values and has methods to add, get or remove unique value references
 */
internal class UniqueIndexValues<DM : IsRootDataModel, T : Comparable<T>>(
    indexReference: ByteArray,
    val numberType: NumberType? = null,
) : AbstractIndexValues<DM, T>(
    indexReference
) {
    override val compareTo: T.(T) -> Int = { other -> compareUniqueValues(this, other, numberType) }
}
