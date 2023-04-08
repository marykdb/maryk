package maryk.datastore.memory.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.processors.datastore.writeToStorage
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update.Addition
import maryk.lib.extensions.compare.compareTo

internal suspend fun <DM : IsRootDataModel> processAdd(
    dataStore: DataStore<DM>,
    dataModel: DM,
    key: Key<DM>,
    version: HLC,
    objectToAdd: Values<DM>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsAddResponseStatus<DM> = try {
    objectToAdd.validate()

    val index = dataStore.records.binarySearch { it.key compareTo key }

    if (index < 0) {
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
        dataModel.Meta.indices?.forEach { indexDefinition ->
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
        recordValues.sortWith { a: DataRecordNode, b: DataRecordNode ->
            a.reference compareTo b.reference
        }

        uniquesToIndex?.forEach { value ->
            dataStore.addToUniqueIndex(dataRecord, value.reference, value.value, version)
        }

        toIndex?.forEach { (indexName, value) ->
            dataStore.addToIndex(dataRecord, indexName, value, version)
        }

        dataStore.records.add((index * -1) - 1, dataRecord)

        val changes = listOf<IsChange>()

        updateSharedFlow.emit(
            Addition(dataModel, key, version.timestamp, objectToAdd.change(changes))
        )

        AddSuccess(key, version.timestamp, changes)
    } else {
        AlreadyExists(key)
    }
} catch (ve: ValidationUmbrellaException) {
    ValidationFail(ve)
} catch (ve: ValidationException) {
    ValidationFail(listOf(ve))
} catch (ue: UniqueException) {
    var index = 0
    val ref = dataModel.getPropertyReferenceByStorageBytes(
        ue.reference.size,
        { ue.reference[index++] }
    )

    ValidationFail(
        listOf(
            AlreadyExistsException(ref, ue.key)
        )
    )
} catch (e: Throwable) {
    ServerFail(e.toString(), e)
}
