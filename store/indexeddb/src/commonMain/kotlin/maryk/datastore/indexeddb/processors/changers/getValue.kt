package maryk.datastore.indexeddb.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue
import maryk.lib.extensions.compare.compareTo

/** Get value from [values] by [reference] */
internal fun <T : Any> getValue(
    values: List<DataRecordNode>,
    reference: ByteArray,
    toVersion: HLC? = null
): DataRecordValue<T>? {
    val valueIndex = values.binarySearch {
        it.reference compareTo reference
    }
    return getValueAtIndex(values, valueIndex, toVersion)
}
