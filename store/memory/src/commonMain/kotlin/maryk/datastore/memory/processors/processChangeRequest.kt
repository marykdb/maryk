@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
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
 * [keepAllVersions] determines if history is kept
 */
private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> applyChanges(
    objectToChange: DataRecord<DM, P>,
    changes: List<IsChange>,
    version: ULong,
    keepAllVersions: Boolean
): IsChangeResponseStatus<DM> {
    try {
        var validationExceptions: MutableList<ValidationException>? = null

        fun addValidationFail(ve: ValidationException) {
            if (validationExceptions == null) {
                validationExceptions = mutableListOf()
            }
            validationExceptions!!.add(ve)
        }

        val valueChangers = mutableListOf<() -> Unit>()

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
                            objectToChange.createSetValue(
                                valueChangers::add, reference, value, version, keepAllVersions
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
                            objectToChange.createDeleteByReference(valueChangers::add, ref, version)  { previousValue ->
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
                            objectToChange.createSetListValue(valueChangers::add, listChange.reference, list, originalCount, version, keepAllVersions)
                        }
                    }
                }
                is SetChange -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        for (setChange in change.setValueChanges) {
                            val setReference = setChange.reference as SetReference<Any, IsPropertyContext>
                            val setDefinition = setReference.propertyDefinition
                            var countChange = 0
                            setChange.addValues?.let {
                                createValidationUmbrellaException({ setReference }) { addException ->
                                    for (value in it) {
                                        val setItemRef = setDefinition.getItemRef(value, setReference)
                                        try {
                                            setDefinition.valueDefinition.validateWithRef(null, value) { setItemRef }
                                            objectToChange.createSetValue(valueChangers::add, setItemRef, value, version)
                                            countChange++
                                        } catch (e: ValidationException) { addException(e) }
                                    }
                                }
                            }
                            setChange.deleteValues?.let {
                                for(value in it) {
                                    val setItemRef = setDefinition.getItemRef(value, setReference)
                                    objectToChange.createDeleteByReference(valueChangers::add, setItemRef, version)
                                    countChange--
                                }
                            }

                            val newSize = objectToChange.createCountUpdater(valueChangers::add, setChange.reference, version, countChange, keepAllVersions)
                            setDefinition.validateSize(newSize) { setReference }
                        }
                    }
                }
                is MapChange -> {
                    if (validationExceptions.isNullOrEmpty()) {
                        for (mapChange in change.mapValueChanges) {
                            @Suppress("UNCHECKED_CAST")
                            val mapReference = mapChange.reference as MapReference<Any, Any, IsPropertyContext>
                            val mapDefinition = mapReference.propertyDefinition.definition
                            var countChange = 0
                            mapChange.valuesToAdd?.let {
                                createValidationUmbrellaException({ mapReference }) { addException ->
                                    for ((key, value) in it) {
                                        val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                        try {
                                            mapDefinition.keyDefinition.validateWithRef(null, key) { mapDefinition.getKeyRef(key, mapReference) }
                                        } catch (e: ValidationException) { addException(e) }
                                        try {
                                            mapDefinition.valueDefinition.validateWithRef(null, value) { mapValueRef }
                                        } catch (e: ValidationException) { addException(e) }

                                        objectToChange.createSetValue(valueChangers::add, mapValueRef, value, version)
                                        countChange++
                                    }
                                }
                            }
                            mapChange.keysToDelete?.let {
                                for(key in it) {
                                    val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                    objectToChange.createDeleteByReference(valueChangers::add, mapValueRef, version)
                                    countChange--
                                }
                            }

                            val newSize = objectToChange.createCountUpdater(valueChangers::add, mapChange.reference, version, countChange, keepAllVersions)
                            mapDefinition.validateSize(newSize) { mapReference }
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

        // Do all value changes now all validations have been done
        for (valueChanger in valueChangers) {
            valueChanger()
        }

        // Nothing skipped out so must be a success
        return Success(version)
    } catch (e: Throwable) {
        return ServerFail(e.toString())
    }
}
