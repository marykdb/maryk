package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.records.index.IndexValues
import maryk.datastore.memory.records.index.UniqueException
import maryk.datastore.memory.records.index.UniqueIndexValues
import maryk.lib.extensions.compare.compareTo

internal typealias AnyDataStore = DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * An in memory data store containing records and indices
 */
internal class DataStore<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    val keepAllVersions: Boolean
) {
    val records: MutableList<DataRecord<DM, P>> = mutableListOf()
    private val indices: MutableList<IndexValues<DM, P>> = mutableListOf()
    private val uniqueIndices: MutableList<UniqueIndexValues<DM, P, Comparable<Any>>> = mutableListOf()

    /** Add [record] to index for [value] and pass [previousValue] so that index reference can be deleted */
    fun addToIndex(
        record: DataRecord<DM, P>,
        indexName: ByteArray,
        value: ByteArray,
        version: ULong,
        previousValue: ByteArray? = null
    ) {
        val index = getOrCreateIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
        index.addToIndex(record, value, version)
    }

    /** Remove [record] for [previousValue] from index */
    fun removeFromIndex(
        record: DataRecord<DM, P>,
        indexName: ByteArray,
        version: ULong,
        previousValue: ByteArray? = null
    ) {
        val index = getOrCreateIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
    }

    /** Add [record] to unique index for [value] and pass [previousValue] so that index reference can be deleted */
    fun addToUniqueIndex(
        record: DataRecord<DM, P>,
        indexName: ByteArray,
        value: Comparable<Any>,
        version: ULong,
        previousValue: Comparable<Any>? = null
    ) {
        val index = getOrCreateUniqueIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
        index.addToIndex(record, value, version)
    }

    /** Remove [dataRecord] from all unique indices and register removal below [version] */
    fun removeFromUniqueIndices(dataRecord: DataRecord<DM, P>, version: ULong) {
        for (indexValues in uniqueIndices) {
            getValue<Comparable<Any>>(dataRecord.values, indexValues.indexReference)?.let {
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

    /** Get unique index for [indexReference] or create it if it does not exist. */
    internal fun getOrCreateIndex(indexReference: ByteArray): IndexValues<DM, P> {
        val i = indices.binarySearch { it.indexReference.compareTo(indexReference) }
        return if (i < 0) {
            IndexValues<DM, P>(indexReference).also {
                indices.add(
                    i * -1 - 1,
                    it
                )
            }
        } else {
            indices[i]
        }
    }

    /** Get unique index for [indexReference] or create it if it does not exist. */
    private fun getOrCreateUniqueIndex(indexReference: ByteArray): UniqueIndexValues<DM, P, Comparable<Any>> {
        val i = uniqueIndices.binarySearch { it.indexReference.compareTo(indexReference) }
        return if (i < 0) {
            UniqueIndexValues<DM, P, Comparable<Any>>(indexReference).also {
                uniqueIndices.add(
                    i * -1 - 1,
                    it
                )
            }
        } else {
            uniqueIndices[i]
        }
    }

    /** Get DataRecord by [key] */
    fun getByKey(key: ByteArray): DataRecord<DM, P>? {
        val index = this.records.binarySearch { it.key.bytes.compareTo(key) }

        return if (index >= 0) this.records[index] else null
    }
}
