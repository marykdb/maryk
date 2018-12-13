@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors.changers

import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue

/**
 * Sets a [value] at a specific [valueIndex] and stores it below [reference] at [version] in [values]
 * Use [keepAllVersions] on true to keep all versions
 * Returns the new DataRecordValue or null if not changed
 */
internal fun <T: Any> setValueAtIndex(
    values: List<DataRecordNode>,
    valueIndex: Int,
    reference: ByteArray,
    value: T,
    version: ULong,
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
            if (keepAllVersions) {
                // Only store value if was not already value
                @Suppress("UNCHECKED_CAST")
                if (matchedValue.value != value) {
                    DataRecordValue(reference, value, version).also {
                        (values as MutableList<DataRecordNode>)[valueIndex] =
                                DataRecordHistoricValues(
                                    reference,
                                    listOf(
                                        matchedValue as DataRecordValue<T>,
                                        it
                                    )
                                )
                    }
                } else null
            } else {
                val lastValue = (values as MutableList<DataRecordNode>).last()
                // Only store value if was not already value
                @Suppress("UNCHECKED_CAST")
                if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                    DataRecordValue(reference, value, version).also {
                        values[valueIndex] = it
                    }
                } else null
            }
        }
        is DeletedValue<*> -> {
            DataRecordValue(reference, value, version).also {
                (values as MutableList<DataRecordNode>)[valueIndex] = it
            }
        }
        is DataRecordHistoricValues<*> -> {
            val lastValue = (values as MutableList<DataRecordNode>).last()
            // Only store value if was not already value
            @Suppress("UNCHECKED_CAST")
            if (lastValue !is DataRecordValue<*> || lastValue.value != value) {
                DataRecordValue(reference, value, version).also {
                    (matchedValue.history as MutableList<DataRecordValue<*>>).add(
                        DataRecordValue(reference, value, version)
                    )
                }
            } else null
        }
    }
}
