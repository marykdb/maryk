package maryk.datastore.indexeddb.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.indexeddb.records.DataRecord

/**
 * Contains all index values and has methods to add, get or remove unique value references
 */
internal abstract class AbstractIndexValues<DM : IsRootDataModel, T : Any>(
    val indexReference: ByteArray
) {
    abstract val compareTo: T.(T) -> Int
    val indexValues = mutableListOf<IsIndexItem<DM, T>>()

    /**
     * Add a [record] [value] reference at [version] to index.
     * Returns true if succeeds or false when already exists
     */
    fun addToIndex(
        record: DataRecord<DM>,
        value: T,
        version: HLC
    ): Boolean {
        val i = indexValues.binarySearch { it.value.compareTo(value) }
        return when {
            i < 0 -> {
                indexValues.add(
                    i * -1 - 1,
                    IndexValue(value, record, version)
                )
                true
            }
            indexValues[i] is HistoricalIndexValue<*, *> -> {
                val lastIndexValue = indexValues[i] as HistoricalIndexValue<DM, T>
                if (lastIndexValue.records.last().record == null) {
                    lastIndexValue.records.add(
                        IndexValue(value, record, version)
                    )
                } else false
            }
            indexValues[i].record == null -> {
                indexValues[i] = IndexValue(value, record, version)
                true
            }
            // Only return if current stored record is the record.
            indexValues[i].record == record -> true
            else -> false
        }
    }

    /**
     * Remove a [record] at [value] from index and records the removal at [version]
     * Use [keepAllVersions] on true to keep historical records
     */
    fun removeFromIndex(
        record: DataRecord<DM>,
        value: T,
        version: HLC,
        keepAllVersions: Boolean
    ): Boolean {
        val i = indexValues.binarySearch { it.value.compareTo(value) }
        return if (i >= 0 && indexValues[i].record == record) {
            val oldValue = indexValues[i]
            if (keepAllVersions) {
                val newValue = RecordAtVersion<DM>(null, version)
                if (oldValue is HistoricalIndexValue<DM, T>) {
                    oldValue.records += newValue
                } else {
                    indexValues[i] = HistoricalIndexValue(
                        value,
                        mutableListOf(oldValue, newValue)
                    )
                }
            } else {
                indexValues[i] = IndexValue(value, null, version)
            }
            true
        } else false
    }

    /** Delete any index of [value] to [record] and return true if an index value was deleted */
    fun deleteHardFromIndex(record: DataRecord<DM>, value: T): Boolean {
        val i = indexValues.binarySearch { it.value.compareTo(value) }
        return if (i >= 0) {
            val oldValue = indexValues[i]
            if (oldValue is HistoricalIndexValue<DM, T>) {
                oldValue.records.removeAll { it.record == record }
            } else {
                indexValues.removeAt(i)
            }
            true
        } else false
    }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T) =
        this.indexValues.binarySearch { it.value.compareTo(value) }.let { index ->
            if (index >= 0) {
                this.indexValues[index].record
            } else {
                null
            }
        }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T, version: HLC) =
        this.indexValues.binarySearch { it.value.compareTo(value) }.let { index ->
            if (index >= 0) {
                this.indexValues[index].recordAtVersion(version)
            } else {
                null
            }
        }
}

