package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

/**
 * Contains all unique index values and has methods to add, remove unique value references and
 * check they don't already exists
 */
internal class UniqueIndexValues<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val reference: ByteArray
) {
    val records = mutableListOf<IsUniqueIndexItem<DM, P>>()

    /**
     * Add a [record] [value] reference at [version] to index.
     * Returns true if succeeds or false when already exists
     */
    fun addToIndex(
        record: DataRecord<DM, P>,
        value: Comparable<Any>,
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
            records[i] is HistoricalUniqueIndexValue<*, *> -> {
                val lastRecord = records[i] as HistoricalUniqueIndexValue<DM, P>
                if (lastRecord.records.last().record == null) {
                    lastRecord.records.add(
                        UniqueIndexValue(value, record, version)
                    )
                } else false
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
        value: DataRecordValue<Comparable<Any>>,
        version: ULong,
        keepAllVersions: Boolean
    ): Boolean {
        val i = records.binarySearch { value.value.compareTo(it.value) }
        return if (i >= 0 && records[i].record == record) {
            val oldValue = records[i]
            if (keepAllVersions) {
                val newValue = UniqueRecordAtVersion<DM, P>( null, version)
                if (oldValue is HistoricalUniqueIndexValue<DM, P>) {
                    oldValue.records += newValue
                } else {
                    // Suppress because of IDE issue
                    @Suppress("RemoveExplicitTypeArguments")
                    records[i] = HistoricalUniqueIndexValue<DM, P>(
                        value.value,
                        mutableListOf(oldValue, newValue)
                    )
                }
            } else {
                records[i] = UniqueIndexValue( value.value, null, version)
            }
            true
        } else false
    }

    /**
     * Validate if unique [value] does not already exists and is not [dataRecord].
     * A deleted value does not exists too.
     * If exists it throws an UniqueException referring to the reference
     */
    fun validateUniqueNotExists(dataRecord: DataRecord<DM, P>, value: DataRecordValue<Comparable<Any>>) {
        val i = records.binarySearch { value.value.compareTo(it.value) }
        if (i >= 0) { // if found
            val record = records[i]
            // if not deleted and not the given record it already exists.
            if (record.record != null && record.record != dataRecord) {
                throw UniqueException(reference)
            }
        }
    }
}

/** Defines that this is an item defining one unique index value and reference */
internal interface IsUniqueIndexItem<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>: IsRecordAtVersion<DM, P> {
    val value: Any
}

/** Defines that this is an [record] reference at a certain [version] */
internal interface IsRecordAtVersion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val record: DataRecord<DM, P>?
    val version: ULong
}

/** Defines a single [record] at [version] for [value]. */
internal class UniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val value: Any,
    override val record: DataRecord<DM, P>?,
    override val version: ULong
): IsUniqueIndexItem<DM, P>, IsRecordAtVersion<DM, P>

/** Unique index with historical [records] for [value] */
internal class HistoricalUniqueIndexValue<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val value: Any,
    val records: MutableList<IsRecordAtVersion<DM, P>>
): IsUniqueIndexItem<DM, P> {
    override val version get() = records.last().version
    override val record get() = records.last().record
}

/** Unique [record] at [version]. Primarily for in indexes */
internal class UniqueRecordAtVersion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val record: DataRecord<DM, P>?,
    override val version: ULong
): IsRecordAtVersion<DM, P>
