package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.values.Values
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.Update.Addition
import maryk.lib.extensions.compare.compareTo

internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAdd(
    key: Key<DM>,
    version: HLC,
    dataModel: DM,
    objectToAdd: Values<DM, P>,
    dataStore: DataStore<DM, P>,
    index: Int,
    updateSendChannel: SendChannel<Update<DM, P>>
): List<IsChange> {
    val recordValues = ArrayList<DataRecordNode>()
    var uniquesToIndex: MutableList<DataRecordValue<Comparable<Any>>>? = null
    var toIndex: MutableMap<ByteArray, ByteArray>? = null
    val dataRecord = DataRecord(
        key = key,
        values = recordValues,
        firstVersion = version,
        lastVersion = version
    )

    // Find new index values to write
    dataModel.indices?.forEach { indexDefinition ->
        val valueBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
            ?: return@forEach // skip if no complete values to index are found

        if (toIndex == null) toIndex = mutableMapOf()
        toIndex?.let {
            it[indexDefinition.referenceStorageByteArray.bytes] = valueBytes
        }
    }

    objectToAdd.writeToStorage { _, reference, definition, value ->
        val dataRecordValue = DataRecordValue(reference, value, version)
        if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
            @Suppress("UNCHECKED_CAST")
            val comparableValue = dataRecordValue as DataRecordValue<Comparable<Any>>
            dataStore.validateUniqueNotExists(comparableValue, dataRecord)
            when (uniquesToIndex) {
                null -> uniquesToIndex = mutableListOf(comparableValue)
                else -> uniquesToIndex!!.add(comparableValue)
            }
        }
        recordValues += dataRecordValue
    }

    // Sort all nodes since some operations like map key values can be unsorted
    recordValues.sortWith(Comparator { a: DataRecordNode, b: DataRecordNode ->
        a.reference.compareTo(b.reference)
    })

    uniquesToIndex?.forEach { value ->
        dataStore.addToUniqueIndex(dataRecord, value.reference, value.value, version)
    }

    toIndex?.forEach { (indexName, value) ->
        dataStore.addToIndex(dataRecord, indexName, value, version)
    }

    dataStore.records.add((index * -1) - 1, dataRecord)

    val changes = listOf<IsChange>()

    updateSendChannel.send(
        Addition(dataModel, key, version.timestamp, objectToAdd.change(changes))
    )
    return changes
}
