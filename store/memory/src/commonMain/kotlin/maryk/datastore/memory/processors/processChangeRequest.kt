@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MapChange
import maryk.core.query.changes.SetChange
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.lib.time.Instant

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processChangeRequest(
    storeAction: ChangeStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val changeRequest = storeAction.request
    val version = Instant.getCurrentEpochTimeInMillis().toULong()

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objectChanges.isNotEmpty()) {
        objectChanges@for (objectChange in changeRequest.objectChanges) {
            val index = dataStore.records.binarySearch { it.key.compareTo(objectChange.key) }
            val objectToChange = dataStore.records[index]

            val lastVersion = objectChange.lastVersion
            // Check if version is within range
            if(lastVersion != null && objectToChange.lastVersion.compareTo(lastVersion) != 0) {
                statuses.add(
                    ValidationFail(
                        listOf(
                            InvalidValueException(null, "Version of object was different than given: ${objectChange.lastVersion} < ${objectToChange.lastVersion}")
                        )
                    )
                )
                continue@objectChanges
            }

            val status: IsChangeResponseStatus<DM> = when {
                index > -1 -> {
                    applyChanges(objectToChange, objectChange.changes, version, dataStore.keepAllVersions)
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

/**
 * Apply [changes] to a specific [objectToChange] and record them as [version]
 * [isWithHistory] determines if history is kept
 */
private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> applyChanges(
    objectToChange: DataRecord<DM, P>,
    changes: List<IsChange>,
    version: ULong,
    isWithHistory: Boolean
): IsChangeResponseStatus<DM> {
    try {
        var validationExceptions: MutableList<ValidationException>? = null

        fun addValidationFail(ve: ValidationException) {
            if (validationExceptions == null) {
                validationExceptions = mutableListOf()
            }
            validationExceptions!!.add(ve)
        }

        for (change in changes) {
            when (change) {
                is Check -> {
                    for ((reference, value) in change.referenceValuePairs) {
                        if (objectToChange[reference] != value) {
                            addValidationFail(
                                InvalidValueException(reference, value.toString())
                            )
                        }
                    }
                }
                is Change -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        for ((reference, value) in change.referenceValuePairs) {
                            objectToChange.setValue(
                                reference, value, version, isWithHistory
                            ) { previousValue ->
                                try {
                                    reference.propertyDefinition.validate(
                                        previousValue = previousValue,
                                        newValue = value,
                                        parentRefFactory = { (reference as? PropertyReference<*, *, *, *>)?.parentReference }
                                    )
                                } catch (e: ValidationException) {
                                    addValidationFail(e)
                                }
                            }
                        }
                    }
                }
                is Delete -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        for (reference in change.references) {
                            @Suppress("UNCHECKED_CAST")
                            val ref = reference as IsPropertyReference<Any, IsPropertyDefinitionWrapper<Any, *, *, *>, Any>
                            objectToChange.deleteByReference(ref, version)  { previousValue ->
                                try {
                                    ref.propertyDefinition.validate(
                                        previousValue = previousValue,
                                        newValue = null,
                                        parentRefFactory = { (reference as? PropertyReference<*, *, *, *>)?.parentReference }
                                    )
                                } catch (e: ValidationException) {
                                    addValidationFail(e)
                                }
                            }
                        }
                    }
                }
                is ListChange -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        for (listChange in change.listValueChanges) {
                            val originalList = objectToChange.getList(listChange.reference)
                            val list = originalList?.toMutableList() ?: mutableListOf()
                            val originalCount = list.size
                            listChange.deleteAtIndex?.let {
                                for(deleteIndex in it) {
                                    list.removeAt(deleteIndex)
                                }
                            }
                            listChange.deleteValues?.let {
                                for(deleteValue in it) {
                                    list.remove(deleteValue)
                                }
                            }
                            listChange.addValuesAtIndex?.let {
                                for((index, value) in it) {
                                    list.add(index, value)
                                }
                            }
                            listChange.addValuesToEnd?.let {
                                for(value in it) {
                                    list.add(value)
                                }
                            }

                            try {
                                @Suppress("UNCHECKED_CAST")
                                (listChange.reference as ListReference<Any, *>).propertyDefinition.validate(
                                    previousValue = originalList,
                                    newValue = list,
                                    parentRefFactory = { (listChange.reference as? PropertyReference<Any, *, *, *>)?.parentReference }
                                )
                            } catch (e: ValidationException) {
                                addValidationFail(e)
                            }
                            objectToChange.setListValue(listChange.reference, list, originalCount, version, isWithHistory)
                        }
                    }
                }
                is SetChange -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        for (setChange in change.setValueChanges) {
                            val setDefinition = setChange.reference.propertyDefinition as SetPropertyDefinitionWrapper<Any, IsPropertyContext, Any>
                            setChange.addValues?.let {
                                for(value in it) {
                                    val setItemRef = setDefinition.getItemRef(value, setChange.reference as SetReference<Any, IsPropertyContext>)
                                    objectToChange.setValue(setItemRef, value, version)
                                }
                            }
                            setChange.deleteValues?.let {
                                for(value in it) {
                                    val setItemRef = setDefinition.getItemRef(value, setChange.reference as SetReference<Any, IsPropertyContext>)
                                    objectToChange.deleteByReference(setItemRef, version)
                                }
                            }
                        }
                    }
                }
                is MapChange -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        for (mapChange in change.mapValueChanges) {
                            @Suppress("UNCHECKED_CAST")
                            val mapReference = mapChange.reference as MapReference<Any, Any, IsPropertyContext>
                            val mapDefinition = mapReference.propertyDefinition.definition
                            mapChange.valuesToAdd?.let {
                                for((key, value) in it) {
                                    val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                    objectToChange.setValue(mapValueRef, value, version)
                                }
                            }
                            mapChange.keysToDelete?.let {
                                for(key in it) {
                                    val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                    objectToChange.deleteByReference(mapValueRef, version)
                                }
                            }
                        }
                    }
                }
                else -> return ServerFail("Unsupported operation $change")
            }
        }

        // Return fail if any validationExceptions were caught
        validationExceptions?.let {
            return ValidationFail(it)
        }

        // Nothing skipped out so must be a success
        return Success(version)
    } catch (e: Throwable) {
        return ServerFail(e.toString())
    }
}
