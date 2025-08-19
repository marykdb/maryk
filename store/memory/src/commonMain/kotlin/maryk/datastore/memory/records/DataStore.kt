package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.records.index.IndexValues
import maryk.datastore.memory.records.index.UniqueIndexValues
import maryk.datastore.shared.UniqueException
import maryk.lib.extensions.compare.compareTo

/**
 * An in memory data store containing records and indexes
 */
internal class DataStore<DM : IsRootDataModel>(
    val keepAllVersions: Boolean
) {
    val records: MutableList<DataRecord<DM>> = mutableListOf()
    private val indexes: MutableList<IndexValues<DM>> = mutableListOf()
    private val uniqueIndices: MutableList<UniqueIndexValues<DM, Comparable<Any>>> = mutableListOf()

    /** Add [record] to index for [value] and pass [previousValue] so that index reference can be deleted */
    internal fun addToIndex(
        record: DataRecord<DM>,
        indexName: ByteArray,
        value: ByteArray,
        version: HLC,
        previousValue: ByteArray? = null
    ) {
        val index = getOrCreateIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
        index.addToIndex(record, value, version)
    }

    /** Remove [record] for [previousValue] from index */
    internal fun removeFromIndex(
        record: DataRecord<DM>,
        indexName: ByteArray,
        version: HLC,
        previousValue: ByteArray? = null
    ) {
        val index = getOrCreateIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
    }

    /** Delete [value] completely from given [indexName] for [record] */
    internal fun deleteHardFromIndex(
        indexName: ByteArray,
        value: ByteArray,
        record: DataRecord<DM>
    ) {
        val index = getOrCreateIndex(indexName)
        index.deleteHardFromIndex(record, value)
    }

    /** Add [record] to unique index for [value] and pass [previousValue] so that index reference can be deleted */
    internal fun addToUniqueIndex(
        record: DataRecord<DM>,
        indexName: ByteArray,
        value: Comparable<Any>,
        version: HLC,
        previousValue: Comparable<Any>? = null
    ) {
        val index = getOrCreateUniqueIndex(indexName)
        previousValue?.let {
            index.removeFromIndex(record, previousValue, version, keepAllVersions)
        }
        index.addToIndex(record, value, version)
    }

    /** Remove [dataRecord] from all unique indexes and register removal below [version] */
    internal fun removeFromUniqueIndices(
        dataRecord: DataRecord<DM>,
        version: HLC,
        hardDelete: Boolean
    ) {
        if (hardDelete) {
            for (indexValues in uniqueIndices) {
                val valueIndex = dataRecord.values.binarySearch {
                    it.reference compareTo indexValues.indexReference
                }
                if (valueIndex >= 0) {
                    when(val dataNode = dataRecord.values[valueIndex]) {
                        is DataRecordHistoricValues<*> -> {
                            dataNode.history.forEach { record ->
                                @Suppress("UNCHECKED_CAST")
                                indexValues.deleteHardFromIndex(
                                    dataRecord,
                                    (record as DataRecordValue<Comparable<Any>>).value
                                )
                            }
                        }
                        is DataRecordValue<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            indexValues.deleteHardFromIndex(dataRecord, (dataNode as DataRecordValue<Comparable<Any>>).value)
                        }
                        else -> {}
                    }
                }
            }
        } else {
            for (indexValues in uniqueIndices) {
                getValue<Comparable<Any>>(dataRecord.values, indexValues.indexReference)?.let {
                    indexValues.removeFromIndex(dataRecord, it.value, version, keepAllVersions)
                }
            }
        }
    }

    /** Validate if value in [dataRecordValue] does not already exist and if it exists it is not [dataRecord] */
    internal fun validateUniqueNotExists(
        dataRecordValue: DataRecordValue<Comparable<Any>>,
        dataRecord: DataRecord<DM>
    ) {
        getOrCreateUniqueIndex(dataRecordValue.reference)[dataRecordValue.value]?.let {
            // if not deleted and not the given record it already exists.
            if (it != dataRecord) {
                throw UniqueException(dataRecordValue.reference, it.key)
            }
        }
    }

    /** Get unique index for [indexReference] or create it if it does not exist. */
    internal fun getOrCreateIndex(indexReference: ByteArray): IndexValues<DM> {
        val i = indexes.binarySearch { it.indexReference compareTo indexReference }
        return if (i < 0) {
            IndexValues<DM>(indexReference).also {
                indexes.add(
                    i * -1 - 1,
                    it
                )
            }
        } else {
            indexes[i]
        }
    }

    /** Get unique index for [indexReference] or create it if it does not exist. */
    internal fun getOrCreateUniqueIndex(indexReference: ByteArray): UniqueIndexValues<DM, Comparable<Any>> {
        val i = uniqueIndices.binarySearch { it.indexReference compareTo indexReference }
        return if (i < 0) {
            UniqueIndexValues<DM, Comparable<Any>>(indexReference).also {
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
    internal fun getByKey(key: ByteArray): DataRecord<DM>? {
        val index = this.records.binarySearch { it.key.bytes compareTo key }

        return if (index >= 0) this.records[index] else null
    }
}
