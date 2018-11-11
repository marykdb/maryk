@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.StoreAction
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.query.changes.Check
import maryk.core.query.changes.IsChange
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.lib.time.Instant

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processChangeRequest(
    storeAction: ChangeStoreAction<DM, P>,
    dataList: MutableList<DataRecord<DM, P>>
) {
    val changeRequest = storeAction.request
    val version = Instant.getCurrentEpochTimeInMillis().toULong()

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objectChanges.isNotEmpty()) {
        for (objectChange in changeRequest.objectChanges) {
            val index = dataList.binarySearch { it.key.compareTo(objectChange.key) }

            val status: IsChangeResponseStatus<DM> = when {
                index > -1 -> {
                    applyChanges(dataList, index, objectChange.changes, version)
                }
                else -> DoesNotExist(objectChange.key)
            }

            statuses.add(status)
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> applyChanges(
    dataList: MutableList<DataRecord<DM, P>>,
    index: Int,
    changes: List<IsChange>,
    version: ULong
): IsChangeResponseStatus<DM> {
    val objectToChange = dataList[index]

    var validationExceptions: MutableList<ValidationException>? = null

    fun addValidationFail(ve: ValidationException) {
        if (validationExceptions == null) {
            validationExceptions = mutableListOf()
        }
        validationExceptions!!.add(ve)
    }

    changes.forEach { change ->
        when(change) {
            is Check -> {
                for ((reference, value) in change.referenceValuePairs) {
                    if (objectToChange[reference] != value) {
                        addValidationFail(
                            InvalidValueException(reference, value.toString())
                        )
                    }
                }
            }
            else -> ServerFail<DM>("Unsupported operation $objectToChange")
        }
    }

    // Return fail if any validationExceptions were caught
    validationExceptions?.let {
        return ValidationFail(it)
    }

    // Nothing skipped out so must be a success
    return Success(version)
}
