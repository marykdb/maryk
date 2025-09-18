package maryk.datastore.indexeddb.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.indexeddb.records.DataRecordHistoricValues
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue
import maryk.datastore.indexeddb.records.DeletedValue
import maryk.datastore.indexeddb.records.IsDataRecordValue

/**
 * Sets a [value] at a specific [valueIndex] and stores it below [reference] at [version] in [values]
 * Use [keepAllVersions] on true to keep all versions
 * Returns the new DataRecordValue or null if not changed
 */
internal fun <T : Any> setValueAtIndex(
    values: List<DataRecordNode>,
    valueIndex: Int,
    reference: ByteArray,
    value: T,
    version: HLC,
    keepAllVersions: Boolean
): DataRecordValue<T>? {
    return if (valueIndex < 0) {
        DataRecordValue(reference, value, version).also {
            // When not found add it
            (values as MutableList<DataRecordNode>).add(
                valueIndex * -1 - 1,
                it
            )
        }
    } else when (val matchedValue = values[valueIndex]) {
        is DataRecordValue<*> -> {
            if (keepAllVersions && matchedValue.version != version) {
                // Only store value if was not already value
                if (matchedValue.value != value) {
                    DataRecordValue(reference, value, version).also {
                        @Suppress("UNCHECKED_CAST")
                        (values as MutableList<DataRecordNode>)[valueIndex] =
                            DataRecordHistoricValues(
                                reference,
                                mutableListOf(matchedValue as IsDataRecordValue<T>),
                                it
                            )
                    }
                } else null
            } else {
                // Only store value if was not already value
                if (matchedValue.value != value) {
                    DataRecordValue(reference, value, version).also {
                        (values as MutableList<DataRecordNode>)[valueIndex] = it
                    }
                } else null
            }
        }
        is DeletedValue<*> -> {
            // Cannot be historic otherwise delete would be written inside DataRecordHistoricValues
            // So simple overwrite
            DataRecordValue(reference, value, version).also {
                (values as MutableList<DataRecordNode>)[valueIndex] = it
            }
        }
        is DataRecordHistoricValues<*> -> {
            val lastValue = matchedValue.toAdd ?: matchedValue.history.last()

            // Only store value if was not already value
            if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                DataRecordValue(reference, value, version).also {
                    @Suppress("UNCHECKED_CAST")
                    (matchedValue as DataRecordHistoricValues<T>).add(it)
                }
            } else null
        }
    }
}
