package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.processors.changers.getValue
import maryk.lib.extensions.compare.compareTo

internal typealias AnyDataStore = DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * An in memory data store containing records and indices
 */
internal class DataStore<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val keepAllVersions: Boolean
) {
    val records: MutableList<DataRecord<DM, P>> = mutableListOf()
    private val uniqueIndices: MutableList<UniqueIndexValues<DM, P, Comparable<Any>>> = mutableListOf()

    /** Add [record] to unique index for [value] and pass [previousValue] so that index reference can be deleted */
    fun addToUniqueIndex(record: DataRecord<DM, P>, value: DataRecordValue<Comparable<Any>>, previousValue: DataRecordValue<Comparable<Any>>? = null) {
        val index = getOrCreateUniqueIndex(value.reference)
        previousValue?.let {
            index.removeFromIndex(record, previousValue.value, value.version, keepAllVersions)
        }
        index.addToIndex(record, value.value, value.version)
    }

    /** Remove [dataRecord] from all unique indices and register removal below [version] */
    fun removeFromUniqueIndices(dataRecord: DataRecord<DM, P>, version: ULong) {
        for (indexValues in uniqueIndices) {
            getValue<Comparable<Any>>(dataRecord.values, indexValues.reference)?.let {
                indexValues.removeFromIndex(dataRecord, it.value, version, keepAllVersions)
            }
        }
    }

    /** Validate if value in [dataRecordValue] does not already exist and if it exists it is not [dataRecord] */
    fun validateUniqueNotExists(
        dataRecordValue: DataRecordValue<Comparable<Any>>,
        dataRecord: DataRecord<DM, P>
    ) {
        getOrCreateUniqueIndex(dataRecordValue.reference)[dataRecordValue.value]?.let {
            // if not deleted and not the given record it already exists.
            if (it != dataRecord) {
                throw UniqueException(dataRecordValue.reference)
            }
        }
    }

    /** Get unique index for [reference] or create it if it does not exist. */
    private fun getOrCreateUniqueIndex(reference: ByteArray): UniqueIndexValues<DM, P, Comparable<Any>> {
        val i = uniqueIndices.binarySearch { it.reference.compareTo(reference) }
        return if (i < 0) {
            UniqueIndexValues<DM, P, Comparable<Any>>(reference).also {
                uniqueIndices.add(
                    i * -1 - 1,
                    UniqueIndexValues(reference)
                )
            }
        } else {
            uniqueIndices[i]
        }
    }
}
