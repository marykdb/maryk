package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

internal class UniqueIndexValues<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val reference: ByteArray
) {
    val records = mutableListOf<UniqueIndexValue<DM, P>>()

    fun addToIndex(record: DataRecord<DM, P>, value: Comparable<Any>): UniqueIndexValue<DM, P> {
        val i = records.binarySearch { value.compareTo(it.value) }
        return if (i < 0) {
            UniqueIndexValue(value, record).also {
                records.add(
                    i * -1 - 1,
                    it
                )
            }
        } else {
            records[i]
        }
    }

    fun removeFromIndex(record: DataRecord<DM, P>, value: DataRecordValue<Comparable<Any>>): UniqueIndexValue<DM, P>? {
        val i = records.binarySearch { value.value.compareTo(it.value) }
        return if (i >= 0 && records[i] == record) {
            records.removeAt(i)
        } else null
    }

    fun validateUniqueNotExists(dataRecord: DataRecord<DM, P>, reference: ByteArray, value: DataRecordValue<Comparable<Any>>) {
        val i = records.binarySearch { value.value.compareTo(it.value) }
        if (i >= 0 && records[i] != dataRecord) { throw UniqueException(reference) }
    }
}

internal class UniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val value: Any,
    val record: DataRecord<DM, P>
)

internal class HistoricalUniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val value: Any,
    val record: DataRecord<DM, P>
)
