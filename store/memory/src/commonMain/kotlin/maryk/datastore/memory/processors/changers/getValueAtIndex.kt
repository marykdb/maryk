package maryk.datastore.memory.processors.changers

import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue

/** Get a value from [values] at [valueIndex] */
@Suppress("UNCHECKED_CAST")
internal fun <T: Any> getValueAtIndex(
    values: List<DataRecordNode>,
    valueIndex: Int
): DataRecordValue<T>? {
    return if (valueIndex < 0) {
        null
    } else  when (val node = values[valueIndex]) {
        is DataRecordValue<*> -> node as DataRecordValue<T>
        is DataRecordHistoricValues<*> -> when (val lastValue = node.history.last()) {
            is DataRecordValue<*> -> lastValue as DataRecordValue<T>
            else -> null // deletion
        }
        else -> null
    }
}
