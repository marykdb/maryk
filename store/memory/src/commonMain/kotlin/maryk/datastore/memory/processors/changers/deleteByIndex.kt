@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors.changers

import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.datastore.memory.records.IsDataRecordValue

/** Delete value at [valueIndex] for [reference] and record it as [version] */
internal fun <T : Any> deleteByIndex(
    values: MutableList<DataRecordNode>,
    valueIndex: Int,
    reference: ByteArray,
    version: ULong
) =
    if (valueIndex < 0) {
        null
    } else {
        when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> {
                DeletedValue<T>(reference, version).also {
                    values[valueIndex] = it
                }
            }
            is DataRecordHistoricValues<*> -> {
                @Suppress("UNCHECKED_CAST")
                DeletedValue<T>(reference, version).also {
                    (matchedValue.history as MutableList<IsDataRecordValue<*>>).add(it)
                }
            }
            is DeletedValue<*> -> matchedValue
        }
    }
