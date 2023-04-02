package maryk.core.values

import maryk.core.models.IsDataModel

/** A Values object with multiple ValueItems */
interface IsValues<DM : IsDataModel> : Iterable<ValueItem>, IsValuesGetter {
    fun original(index: UInt): Any?
}
