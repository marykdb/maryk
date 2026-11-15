package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.lastVersionIndex

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
                val historicValue = node.history.getOrNull(node.history.lastVersionIndex(toVersion) { it.version })
                historicValue as? DataRecordValue<T>
            }
        else -> null // Unknown type
    }
}
