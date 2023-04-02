package maryk.core.values

import maryk.core.models.IsDataModel

/** A Values object with multiple ValueItems */
interface IsValues<P : IsDataModel> : Iterable<ValueItem>, IsValuesGetter {
    fun original(index: UInt): Any?
}
