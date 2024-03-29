package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
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
