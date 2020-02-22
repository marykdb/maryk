package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.UniqueException
import maryk.lib.extensions.compare.compareTo

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes an AddRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAddRequest(
    storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>,
    dataStoreFetcher: IsStoreFetcher<*, *>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    val version = storeAction.version

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(addRequest.dataModel) as DataStore<DM, P>

    if (addRequest.objects.isNotEmpty()) {
        for (objectToAdd in addRequest.objects) {
            try {
                objectToAdd.validate()

                val key = addRequest.dataModel.key(objectToAdd)

                val index = dataStore.records.binarySearch { it.key.compareTo(key) }

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
                    addRequest.dataModel.indices?.forEach { indexDefinition ->
                        val valueBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
                            ?: return@forEach // skip if no complete values to index are found

                        if (toIndex == null) toIndex = mutableMapOf()
                        toIndex?.let {
                            it[indexDefinition.toReferenceStorageByteArray()] = valueBytes
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
                    statuses.add(
                        AddSuccess(key, version.timestamp, listOf())
                    )
                } else {
                    statuses.add(
                        AlreadyExists(key)
                    )
                }
            } catch (ve: ValidationUmbrellaException) {
                statuses.add(
                    ValidationFail(ve)
                )
            } catch (ve: ValidationException) {
                statuses.add(
                    ValidationFail(listOf(ve))
                )
            } catch (ue: UniqueException) {
                var index = 0
                val ref = addRequest.dataModel.getPropertyReferenceByStorageBytes(
                    ue.reference.size,
                    { ue.reference[index++] }
                )

                statuses.add(
                    ValidationFail(
                        listOf(
                            AlreadyExistsException(ref, ue.key)
                        )
                    )
                )
            } catch (e: Throwable) {
                statuses.add(
                    ServerFail(e.toString(), e)
                )
            }
        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
