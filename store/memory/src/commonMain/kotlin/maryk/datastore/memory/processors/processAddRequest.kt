@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.walkForStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.IsDataRecordNode
import maryk.lib.time.Instant

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processAddRequest(storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>, dataList: MutableList<DataRecord<*, *>>) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    val version = Instant.getCurrentEpochTimeInMillis().toULong()

    if (addRequest.objectsToAdd.isNotEmpty()) {
        for (objectToAdd in addRequest.objectsToAdd) {
            try {
                objectToAdd.validate()

                val key = addRequest.dataModel.key(objectToAdd)

                val index = dataList.binarySearch { it.key.compareTo(key) }

                if (index < 0) {
                    val recordValues = ArrayList<IsDataRecordNode>()
                    objectToAdd.walkForStorage { _, reference, _, value ->
                        recordValues += DataRecordValue(reference, value, version)
                    }

                    dataList.add(
                        (index * -1) - 1,
                        DataRecord(
                            key = key,
                            values = recordValues,
                            firstVersion = version,
                            lastVersion = version
                        )
                    )
                    statuses.add(
                        AddSuccess(key, version, listOf())
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
            } catch (e: Throwable) {
                statuses.add(
                    ServerFail(e.toString())
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