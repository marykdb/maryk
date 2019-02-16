package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

/**
 * Contains all unique index values and has methods to add, remove unique value references and
 * check they don't already exists
 */
internal class UniqueIndexValues<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Comparable<T>>(
    val reference: ByteArray
) {
    val records = mutableListOf<IsUniqueIndexItem<DM, P, T>>()

    /**
     * Add a [record] [value] reference at [version] to index.
     * Returns true if succeeds or false when already exists
     */
    fun addToIndex(
        record: DataRecord<DM, P>,
        value: T,
        version: ULong
    ): Boolean {
        val i = records.binarySearch { value.compareTo(it.value) }
        return when {
            i < 0 -> {
                records.add(
                    i * -1 - 1,
                    UniqueIndexValue(value, record, version)
                )
                true
            }
            records[i] is HistoricalUniqueIndexValue<*, *, *> -> {
                val lastRecord = records[i] as HistoricalUniqueIndexValue<DM, P, T>
                if (lastRecord.records.last().record == null) {
                    lastRecord.records.add(
                        UniqueIndexValue(value, record, version)
                    )
                } else false
            }
            records[i].record == null -> {
                records[i] = UniqueIndexValue(value, record, version)
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
        val i = records.binarySearch { value.compareTo(it.value) }
        return if (i >= 0 && records[i].record == record) {
            val oldValue = records[i]
            if (keepAllVersions) {
                val newValue = UniqueRecordAtVersion<DM, P>( null, version)
                if (oldValue is HistoricalUniqueIndexValue<DM, P, T>) {
                    oldValue.records += newValue
                } else {
                    records[i] = HistoricalUniqueIndexValue(
                        value,
                        mutableListOf(oldValue, newValue)
                    )
                }
            } else {
                records[i] = UniqueIndexValue( value, null, version)
            }
            true
        } else false
    }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T) =
        this.records.binarySearch { value.compareTo(it.value) }.let { index ->
            if (index >= 0) {
                this.records[index].record
            } else {
                null
            }
        }

    /** Get DataRecord for [value] if exists or null */
    operator fun get(value: T, version: ULong) =
        this.records.binarySearch { value.compareTo(it.value) }.let { index ->
            if (index >= 0) {
                this.records[index].recordAtVersion(version)
            } else {
                null
            }
        }
}

/** Defines that this is an item defining one unique index value and reference */
internal interface IsUniqueIndexItem<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Comparable<T>>: IsRecordAtVersion<DM, P> {
    val value: T
}

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val record: DataRecord<DM, P>?
    val version: ULong
    fun recordAtVersion(version: ULong) =
        if (version >= this.version) { this.record } else null
}

/** Defines a single [record] at [version] for [value]. */
internal class UniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Comparable<T>>(
    override val value: T,
    override val record: DataRecord<DM, P>?,
    override val version: ULong
): IsUniqueIndexItem<DM, P, T>, IsRecordAtVersion<DM, P>

/** Unique index with historical [records] for [value] */
internal class HistoricalUniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, T: Comparable<T>>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM, P>>
): IsUniqueIndexItem<DM, P, T> {
    override val version get() = records.last().version
    override val record get() = records.last().record
    override fun recordAtVersion(version: ULong) =
        records.findLast { version >= it.version }?.record
}

/** Unique [record] at [version]. Primarily for in indexes */
internal class UniqueRecordAtVersion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val record: DataRecord<DM, P>?,
    override val version: ULong
): IsRecordAtVersion<DM, P>
