package maryk.datastore.indexeddb.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.indexeddb.records.DataRecordHistoricValues
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue

/** Get a value from [values] at [valueIndex] */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> getValueAtIndex(
    values: List<DataRecordNode>,
    valueIndex: Int,
    toVersion: HLC? = null
): DataRecordValue<T>? {
    return if (valueIndex < 0) {
        null
    } else when (val node = values[valueIndex]) {
        is DataRecordValue<*> -> if (toVersion != null) {
            // Get value if fits in version range
            if (node.version <= toVersion) node as DataRecordValue<T> else null
        } else node as DataRecordValue<T>
        is DataRecordHistoricValues<*> ->
            if (toVersion == null) {
                // Just get latest value
                when (val lastValue = node.toAdd ?: node.history.last()) {
                    is DataRecordValue<*> -> lastValue as DataRecordValue<T>
                    else -> null // deletion
                }
            } else {
                // Get historic value max at given value
                for (historicValue in node.history.asReversed()) {
                    if (historicValue.version <= toVersion) {
                        return when (historicValue) {
                            is DataRecordValue<*> -> historicValue as DataRecordValue<T>
                            else -> null // deletion
                        }
                    }
                }
                null // not found
            }
        else -> null // Unknown type
    }
}
