package maryk.datastore.memory.records.index

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataRecord

/**
 * Contains all index values and has methods to add, get or remove unique value references
 */
internal abstract class AbstractIndexValues<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions, T : Any>(
    val indexReference: ByteArray
) {
    abstract val compareTo: T.(T) -> Int
    val records = mutableListOf<IsIndexItem<DM, P, T>>()

    /**
     * Add a [record] [value] reference at [version] to index.
     * Returns true if succeeds or false when already exists
     */
    fun addToIndex(
        record: DataRecord<DM, P>,
        value: T,
        version: ULong
    ): Boolean {
        val i = records.binarySearch { it.value.compareTo(value) }
        return when {
            i < 0 -> {
                records.add(
                    i * -1 - 1,
                    IndexValue(value, record, version)
                )
                true
            }
            records[i] is HistoricalIndexValue<*, *, *> -> {
                val lastRecord = records[i] as HistoricalIndexValue<DM, P, T>
                if (lastRecord.records.last().record == null) {
                    lastRecord.records.add(
                        IndexValue(value, record, version)
                    )
                } else false
            }
            records[i].record == null -> {
                records[i] = IndexValue(value, record, version)
                true
            }
            // Only return if current stored record is the record.
            records[i].record == record -> true
            else -> false
        }
    }

    /**
     * Remove a [record] at [value] from index and records the removal at [version]
     * Use [keepAllVersions] on true to keep historical records
     */
    fun removeFromIndex(
        record: DataRecord<DM, P>,
        value: T,
        version: ULong,
        keepAllVersions: Boolean
    ): Boolean {
        val i = records.binarySearch { it.value.compareTo(value) }
        return if (i >= 0 && records[i].record == record) {
            val oldValue = records[i]
            if (keepAllVersions) {
                val newValue = RecordAtVersion<DM, P>(null, version)
                if (oldValue is HistoricalIndexValue<DM, P, T>) {
                    oldValue.records += newValue
                } else {
                    records[i] = HistoricalIndexValue(
                        value,
                        mutableListOf(oldValue, newValue)
                    )
                }
            } else {
                records[i] = IndexValue(value, null, version)
            }
            true
        } else false
    }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T) =
        this.records.binarySearch { it.value.compareTo(value) }.let { index ->
            if (index >= 0) {
                this.records[index].record
            } else {
                null
            }
        }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T, version: ULong) =
        this.records.binarySearch { it.value.compareTo(value) }.let { index ->
            if (index >= 0) {
                this.records[index].recordAtVersion(version)
            } else {
                null
            }
        }
}

