@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.lib.extensions.compare.compareTo

internal typealias AnyDataStore = DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * An in memory data store containing records and indices
 */
internal class DataStore<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val records: MutableList<DataRecord<DM, P>> = mutableListOf()
    val uniqueIndices: MutableList<UniqueIndexValues<DM, P>> = mutableListOf()

    fun addToUniqueIndex(record: DataRecord<DM, P>, value: DataRecordValue<Comparable<Any>>, previousValue: DataRecordValue<Comparable<Any>>? = null) {
        val index = getOrCreateUniqueIndex(value.reference)
        previousValue?.let {
            index.removeFromIndex(record, previousValue)
        }
        index.addToIndex(record, value.value)
    }

    private fun getOrCreateUniqueIndex(reference: ByteArray): UniqueIndexValues<DM, P> {
        val i = uniqueIndices.binarySearch { it.reference.compareTo(reference) }
        return if (i < 0) {
            UniqueIndexValues<DM, P>(reference).also {
                uniqueIndices.add(
                    i * -1 - 1,
                    UniqueIndexValues(reference)
                )
            }
        } else {
            uniqueIndices[i]
        }
    }

    fun validateUniqueNotExists(dataRecord: DataRecord<DM, P>, reference: ByteArray, dataRecordValue: DataRecordValue<Comparable<Any>>) {
        getOrCreateUniqueIndex(dataRecordValue.reference).validateUniqueNotExists(dataRecord, reference, dataRecordValue)
    }

    fun removeFromUniqueIndices(dataRecord: DataRecord<DM, P>) {
        for (indexValues in uniqueIndices) {
            dataRecord.getValue<Comparable<Any>>(indexValues.reference)?.let {
                indexValues.removeFromIndex(dataRecord, it)
            }
        }
    }
}
