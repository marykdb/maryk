package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.records.index.IndexValues
import maryk.datastore.memory.records.index.UniqueIndexValues
import maryk.datastore.shared.UniqueException
import maryk.lib.extensions.compare.compareTo

/**
 * An in memory data store containing records and indexes
 */
internal class DataStore<DM : IsRootDataModel>(
    val keepAllVersions: Boolean,
    val keepUpdateHistoryIndex: Boolean
) {
    data class HardDeletedRecord<DM : IsRootDataModel>(
        val record: DataRecord<DM>,
        val deletedAtVersion: HLC
    )

    data class UpdateHistoryRecord(
        val version: ULong,
        val keyBytes: ByteArray,
        val isHardDelete: Boolean = false
    )

    val records: MutableList<DataRecord<DM>> = mutableListOf()
    val hardDeletedRecords: MutableList<HardDeletedRecord<DM>> = mutableListOf()
    val updateHistory = mutableListOf<UpdateHistoryRecord>()
    private val indexes: MutableList<IndexValues<DM>> = mutableListOf()
    private val uniqueIndices: MutableList<UniqueIndexValues<DM, Comparable<Any>>> = mutableListOf()

    internal fun addToUpdateHistory(version: HLC, keyBytes: ByteArray, isHardDelete: Boolean = false) {
        if (keepUpdateHistoryIndex) {
            updateHistory.add(0, UpdateHistoryRecord(version.timestamp, keyBytes.copyOf(), isHardDelete))
        }
    }

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
        hardDelete: Boolean = false
    ) {
        for (indexValues in uniqueIndices) {
            getValue<Comparable<Any>>(dataRecord.values, indexValues.indexReference)?.let {
                if (hardDelete) {
                    indexValues.deleteHardFromIndex(dataRecord, it.value)
                } else {
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

    /** Get DataRecord by [key] visible at [toVersion] */
    internal fun getByKeyAtVersion(key: ByteArray, toVersion: HLC?): DataRecord<DM>? {
        val currentRecord = getByKey(key)
        if (currentRecord != null && (toVersion == null || currentRecord.firstVersion <= toVersion)) {
            return currentRecord
        }

        return getHardDeletedRecordByKey(key, toVersion)
    }

    /** Get hard deleted DataRecord by [key] when [toVersion] still points before its deletion */
    internal fun getHardDeletedRecordByKey(key: ByteArray, toVersion: HLC?): DataRecord<DM>? {
        if (toVersion == null) return null

        for (index in hardDeletedRecords.lastIndex downTo 0) {
            val deletedRecord = hardDeletedRecords[index]
            val keyCompare = deletedRecord.record.key.bytes compareTo key
            if (keyCompare == 0 &&
                deletedRecord.record.firstVersion <= toVersion &&
                toVersion < deletedRecord.deletedAtVersion
            ) {
                return deletedRecord.record
            }
        }

        return null
    }

    /**
     * Get all record incarnations for [key] relevant to [toVersion].
     * If a current record exists at or before [toVersion], include older archived incarnations too.
     * Otherwise keep the existing visible-record behavior and only return the single archived incarnation
     * visible at [toVersion], if any.
     */
    internal fun getRecordHistoryByKey(key: ByteArray, toVersion: HLC?): List<DataRecord<DM>> {
        val currentRecord = getByKey(key)

        if (currentRecord != null && (toVersion == null || currentRecord.firstVersion <= toVersion)) {
            val previousRecords = hardDeletedRecords.mapNotNull { deletedRecord ->
                deletedRecord.record.takeIf {
                    it.key.bytes compareTo key == 0 && it.firstVersion < currentRecord.firstVersion
                }
            }

            return (previousRecords + currentRecord).sortedBy { it.firstVersion.timestamp }
        }

        return getHardDeletedRecordByKey(key, toVersion)?.let(::listOf) ?: emptyList()
    }

    /** Check if any currently visible key was hard deleted before and later reused. */
    internal fun hasReusedKeys(): Boolean {
        if (hardDeletedRecords.isEmpty() || records.isEmpty()) return false

        val currentKeys = records.map { Bytes(it.key.bytes) }.toHashSet()
        return hardDeletedRecords.any { Bytes(it.record.key.bytes) in currentKeys }
    }

    /** Get all records visible at [toVersion], sorted by key */
    internal fun getRecordsAtVersion(toVersion: HLC?): List<DataRecord<DM>> {
        if (toVersion == null) return records

        val visibleRecords = mutableListOf<DataRecord<DM>>()
        val visibleKeys = mutableSetOf<Bytes>()

        for (record in records) {
            if (record.firstVersion <= toVersion) {
                visibleRecords += record
                visibleKeys += Bytes(record.key.bytes)
            }
        }

        for (index in hardDeletedRecords.lastIndex downTo 0) {
            val deletedRecord = hardDeletedRecords[index]
            val key = Bytes(deletedRecord.record.key.bytes)
            if (key in visibleKeys) continue

            if (deletedRecord.record.firstVersion <= toVersion && toVersion < deletedRecord.deletedAtVersion) {
                visibleRecords += deletedRecord.record
                visibleKeys += key
            }
        }

        visibleRecords.sortBy { Bytes(it.key.bytes) }
        return visibleRecords
    }
}
