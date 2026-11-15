package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.memory.records.index.AbstractIndexValues
import maryk.datastore.memory.records.index.HistoricalIndexValue
import maryk.datastore.memory.records.index.IsIndexItem
import maryk.datastore.memory.records.lastVersionIndex

internal fun <DM : IsRootDataModel, T : Any> AbstractIndexValues<DM, T>.resolveRecordForValue(
    value: T,
    toVersion: HLC?,
    includeSoftDeleted: Boolean,
    dataStore: DataStore<DM>
): DataRecord<DM>? {
    val itemIndex = this.indexValues.binarySearch { this.compareTo.invoke(it.value, value) }
    if (itemIndex < 0) return null

    return resolveIndexRecord(dataStore, this.indexValues[itemIndex], toVersion, includeSoftDeleted)
}

internal fun <DM : IsRootDataModel, T : Any> resolveIndexRecord(
    dataStore: DataStore<DM>,
    item: IsIndexItem<DM, T>,
    toVersion: HLC?,
    includeSoftDeleted: Boolean
): DataRecord<DM>? {
    when (toVersion) {
        null -> item.record?.let { return dataStore.getByKey(it.key) ?: it }
        else -> item.recordAtVersion(toVersion)?.let { return it }
    }

    if (!includeSoftDeleted) return null

    val historicalItem = item as? HistoricalIndexValue<DM, T> ?: return null
    val deletedCandidate = when (toVersion) {
        null -> historicalItem.records.lastOrNull { it.record != null }?.record
        else -> {
            val history = historicalItem.historyByVersion
            val lastIndex = history.lastVersionIndex(toVersion) { it.version }
            (lastIndex downTo 0).firstNotNullOfOrNull { history[it].record }
        }
    } ?: return null

    val canonicalRecord = dataStore.getByKey(deletedCandidate.key) ?: return null
    return canonicalRecord.takeIf { it.isDeleted(toVersion) }
}
