package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
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
    version: HLC,
    keepAllVersions: Boolean
) =
    if (valueIndex < 0) {
        null
    } else {
        when (val matchedValue = values[valueIndex]) {
            is DataRecordValue<*> -> {
                DeletedValue<T>(reference, version).also { newNode ->
                    values[valueIndex] = if (keepAllVersions) {
                        @Suppress("UNCHECKED_CAST")
                        DataRecordHistoricValues(
                            reference,
                            mutableListOf(matchedValue as IsDataRecordValue<T>),
                            newNode
                        )
                    } else {
                        newNode
                    }
                }
            }
            is DataRecordHistoricValues<*> -> {
                DeletedValue<T>(reference, version).also {
                    @Suppress("UNCHECKED_CAST")
                    (matchedValue as DataRecordHistoricValues<T>).add(it)
                }
            }
            is DeletedValue<*> -> matchedValue
        }
    }
