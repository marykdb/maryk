package maryk.datastore.memory.records

import maryk.datastore.memory.records.index.AbstractIndexValues
import maryk.datastore.memory.records.index.HistoricalIndexValue
import maryk.datastore.memory.records.index.IndexValue
import maryk.datastore.memory.records.index.RecordAtVersion
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
    private val hardDeleteTombstones = mutableMapOf<Bytes, HLC>()
    val updateHistory = mutableListOf<UpdateHistoryRecord>()
    private val indexes: MutableList<IndexValues<DM>> = mutableListOf()
    private val uniqueIndices: MutableList<UniqueIndexValues<DM, Comparable<Any>>> = mutableListOf()

    /** Only targeted records are deep-copied. Untouched records and immutable history remain shared. */
    fun copyForAtomic(targetKeys: Set<Bytes>, budget: (Int) -> Unit): DataStore<DM> {
        val copy = DataStore<DM>(keepAllVersions, keepUpdateHistoryIndex)
        val originals = records.filter { Bytes(it.key.bytes) in targetKeys }.associateBy { Bytes(it.key.bytes) }
        val copies = mutableMapOf<Bytes, DataRecord<DM>>()
        fun record(original: DataRecord<DM>): DataRecord<DM> {
            if (originals[Bytes(original.key.bytes)] !== original) return original
            return copies.getOrPut(Bytes(original.key.bytes)) {
                budget(1 + original.values.size)
                original.copy(values = original.values.map { node ->
                    if (node is DataRecordHistoricValues<*>) {
                        budget(node.history.size)
                        node.copyForChange()
                    } else node
                })
            }
        }
        copy.records.addAll(records.map(::record))
        copy.hardDeletedRecords.addAll(hardDeletedRecords.map { HardDeletedRecord(record(it.record), it.deletedAtVersion) })
        copy.hardDeleteTombstones.putAll(hardDeleteTombstones)
        copy.updateHistory.addAll(updateHistory)
        fun <T : Any> index(from: AbstractIndexValues<DM, T>, to: AbstractIndexValues<DM, T>) {
            to.indexValues.addAll(from.indexValues.map { item ->
                if (item is HistoricalIndexValue<DM, T>) {
                    val history = if (item.records.any { entry ->
                        entry.record?.let { originals[Bytes(it.key.bytes)] === it } == true
                    }) {
                        budget(item.records.size)
                        item.records.map { entry ->
                            val mapped = entry.record?.let(::record)
                            if (mapped === entry.record) entry else RecordAtVersion(mapped, entry.version)
                        }.toMutableList()
                    } else AtomicHistoryList(item.records, budget)
                    HistoricalIndexValue(item.value, history)
                } else {
                    val mapped = item.record?.let(::record)
                    if (mapped === item.record) item else IndexValue(item.value, mapped, item.version)
                }
            })
        }
        indexes.forEach { from ->
            IndexValues<DM>(from.indexReference).also { index(from, it); copy.indexes.add(it) }
        }
        uniqueIndices.forEach { from ->
            UniqueIndexValues<DM, Comparable<Any>>(from.indexReference).also { index(from, it); copy.uniqueIndices.add(it) }
        }
        return copy
    }

    fun finishAtomicCopy() {
        fun <T : Any> finish(index: AbstractIndexValues<DM, T>) {
            index.indexValues.forEachIndexed { i, item ->
                if (item is HistoricalIndexValue<DM, T>) {
                    val records = item.records as? AtomicHistoryList ?: return@forEachIndexed
                    index.indexValues[i] = HistoricalIndexValue(item.value, records.committed())
                }
            }
        }
        indexes.forEach(::finish)
        uniqueIndices.forEach(::finish)
    }

    internal fun addToUpdateHistory(version: HLC, keyBytes: ByteArray, isHardDelete: Boolean = false) {
        if (keepUpdateHistoryIndex) {
            val historyRecord = UpdateHistoryRecord(version.timestamp, keyBytes.copyOf(), isHardDelete)
            val insertionIndex = updateHistory.indexOfFirst { it.version <= version.timestamp }
            if (insertionIndex < 0) {
                updateHistory.add(historyRecord)
            } else {
                updateHistory.add(insertionIndex, historyRecord)
            }
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

    /** Remove [record] from a unique index for its [previousValue]. */
    internal fun removeFromUniqueIndex(
        record: DataRecord<DM>,
        indexName: ByteArray,
        previousValue: Comparable<Any>,
        version: HLC
    ) {
        getOrCreateUniqueIndex(indexName).removeFromIndex(record, previousValue, version, keepAllVersions)
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

    /** Validate that all current unique values in [values] are available for [dataRecord]. */
    internal fun validateUniqueValuesNotExists(
        dataRecord: DataRecord<DM>,
        values: List<DataRecordNode>
    ) {
        for (indexValues in uniqueIndices) {
            getValue<Comparable<Any>>(values, indexValues.indexReference)?.let { value ->
                indexValues[value.value]?.let { existingRecord ->
                    if (existingRecord.key != dataRecord.key) {
                        throw UniqueException(indexValues.indexReference, existingRecord.key)
                    }
                }
            }
        }
    }

    /** Add all current unique values in [dataRecord] back to their indexes. */
    internal fun addToUniqueIndices(dataRecord: DataRecord<DM>, version: HLC) {
        for (indexValues in uniqueIndices) {
            getValue<Comparable<Any>>(dataRecord.values, indexValues.indexReference)?.let { value ->
                addToUniqueIndex(dataRecord, indexValues.indexReference, value.value, version)
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

    internal fun lastAppliedVersion(key: ByteArray): HLC? {
        val recordVersion = getByKey(key)?.lastVersion
        val tombstoneVersion = hardDeleteTombstones[Bytes(key)]
        return when {
            recordVersion == null -> tombstoneVersion
            tombstoneVersion == null || recordVersion >= tombstoneVersion -> recordVersion
            else -> tombstoneVersion
        }
    }

    internal fun recordHardDelete(key: ByteArray, version: HLC) {
        val keyBytes = Bytes(key)
        if (version > (hardDeleteTombstones[keyBytes] ?: HLC(0uL))) {
            hardDeleteTombstones[keyBytes] = version
        }
    }

    internal fun clearHardDeleteTombstone(key: ByteArray) {
        hardDeleteTombstones.remove(Bytes(key))
    }

    internal fun compactHardDeleteTombstones(upToVersionInclusive: ULong) {
        hardDeleteTombstones.entries.removeAll { (_, version) ->
            version.timestamp <= upToVersionInclusive
        }
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
