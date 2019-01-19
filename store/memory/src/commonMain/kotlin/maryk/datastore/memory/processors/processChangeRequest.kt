@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
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
import maryk.datastore.memory.processors.changers.createCountUpdater
import maryk.datastore.memory.processors.changers.deleteByReference
import maryk.datastore.memory.processors.changers.getList
import maryk.datastore.memory.processors.changers.setListValue
import maryk.datastore.memory.processors.changers.setValue
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.memory.records.UniqueException
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
                    applyChanges(changeRequest.dataModel, dataStore, objectToChange, objectChange.changes, version, dataStore.keepAllVersions)
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
    dataModel: DM,
    dataStore: DataStore<DM, P>,
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

        var uniquesToProcess: MutableList<DataRecordValue<Comparable<Any>>>? = null

        val newValueList = objectToChange.values.toMutableList()

        var isChanged = false
        val setChanged = { didChange: Boolean -> if (didChange) isChanged = true }

        for (change in changes) {
            try {
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
                        for ((reference, value) in change.referenceValuePairs) {
                            setValue(
                                newValueList, reference, value, version, keepAllVersions
                            ) { dataRecordValue, previousValue ->
                                val definition = reference.comparablePropertyDefinition
                                if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                                    @Suppress("UNCHECKED_CAST")
                                    val comparableValue = dataRecordValue as DataRecordValue<Comparable<Any>>
                                    dataStore.validateUniqueNotExists(comparableValue, objectToChange)
                                    when (uniquesToProcess) {
                                        null -> uniquesToProcess = mutableListOf(comparableValue)
                                        else -> uniquesToProcess!!.add(comparableValue)
                                    }
                                }

                                try {
                                    reference.propertyDefinition.validateWithRef(
                                        previousValue = previousValue,
                                        newValue = value,
                                        refGetter = { reference }
                                    )
                                } catch (e: ValidationException) {
                                    addValidationFail(e)
                                }
                            }.also(setChanged)
                        }
                    }
                    is Delete -> {
                        for (reference in change.references) {
                            @Suppress("UNCHECKED_CAST")
                            val ref =
                                reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
                            deleteByReference(newValueList, ref, version, keepAllVersions) { _, previousValue ->
                                try {
                                    ref.propertyDefinition.validateWithRef(
                                        previousValue = previousValue,
                                        newValue = null,
                                        refGetter = { ref }
                                    )
                                } catch (e: ValidationException) {
                                    addValidationFail(e)
                                }
                            }.also(setChanged)
                        }
                    }
                    is ListChange -> {
                        for (listChange in change.listValueChanges) {
                            val originalList = getList(newValueList, listChange.reference)
                            val list = originalList?.toMutableList() ?: mutableListOf()
                            val originalCount = list.size
                            listChange.deleteAtIndex?.let {
                                for (deleteIndex in it) {
                                    list.removeAt(deleteIndex)
                                }
                            }
                            listChange.deleteValues?.let {
                                for (deleteValue in it) {
                                    list.remove(deleteValue)
                                }
                            }
                            listChange.addValuesAtIndex?.let {
                                for ((index, value) in it) {
                                    list.add(index, value)
                                }
                            }
                            listChange.addValuesToEnd?.let {
                                for (value in it) {
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
                            setListValue(
                                newValueList,
                                listChange.reference,
                                list,
                                originalCount,
                                version,
                                keepAllVersions
                            ).also(setChanged)
                        }
                    }
                    is SetChange -> {
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
                                            setValue(
                                                newValueList,
                                                setItemRef,
                                                value,
                                                version
                                            ) { _, prevValue ->
                                                prevValue ?: countChange++ // Only count up when value did not exist
                                            }.also(setChanged)
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }
                                    }
                                }
                            }
                            setChange.deleteValues?.let {
                                for (value in it) {
                                    val setItemRef = setDefinition.getItemRef(value, setReference)
                                    deleteByReference(newValueList, setItemRef, version, keepAllVersions) { _, prevValue ->
                                        prevValue?.let {
                                            countChange-- // only count down if value existed
                                        }
                                    }.also(setChanged)
                                }
                            }

                            createCountUpdater(
                                newValueList,
                                setChange.reference,
                                version,
                                countChange,
                                keepAllVersions
                            ) {
                                setDefinition.validateSize(it) { setReference }
                            }
                        }
                    }
                    is MapChange -> {
                        for (mapChange in change.mapValueChanges) {
                            @Suppress("UNCHECKED_CAST")
                            val mapReference = mapChange.reference as MapReference<Any, Any, IsPropertyContext>
                            val mapDefinition = mapReference.propertyDefinition.definition
                            var countChange = 0
                            // First keys to delete since they don't change indices because of tombstones
                            mapChange.keysToDelete?.let {
                                for (key in it) {
                                    val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                    deleteByReference(newValueList, mapValueRef, version, keepAllVersions) { _, prevValue ->
                                        prevValue?.let {
                                            countChange-- // only count down if value existed
                                        }
                                    }.also(setChanged)
                                }
                            }
                            mapChange.valuesToSet?.let {
                                createValidationUmbrellaException({ mapReference }) { addException ->
                                    for ((key, value) in it) {
                                        val mapValueRef = mapDefinition.getValueRef(key, mapReference)
                                        try {
                                            mapDefinition.keyDefinition.validateWithRef(
                                                null,
                                                key
                                            ) { mapDefinition.getKeyRef(key, mapReference) }
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }
                                        try {
                                            mapDefinition.valueDefinition.validateWithRef(
                                                null,
                                                value
                                            ) { mapValueRef }
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }

                                        setValue(
                                            newValueList,
                                            mapValueRef,
                                            value,
                                            version
                                        ) { _, prevValue ->
                                            prevValue ?: countChange++ // Only count up when value did not exist
                                        }.also(setChanged)
                                    }
                                }
                            }

                            createCountUpdater(
                                newValueList,
                                mapChange.reference,
                                version,
                                countChange,
                                keepAllVersions
                            ) {
                                mapDefinition.validateSize(it) { mapReference }
                            }
                        }
                    }
                    else -> return ServerFail("Unsupported operation $change")
                }
            } catch (e: ValidationUmbrellaException) {
                for (it in e.exceptions) {
                    addValidationFail(it)
                }
            } catch (e: ValidationException) {
                addValidationFail(e)
            } catch (ue: UniqueException) {
                var index = 0
                val ref = dataModel.getPropertyReferenceByStorageBytes(
                    ue.reference.size,
                    { ue.reference[index++] }
                )

                addValidationFail(
                    AlreadySetException(ref)
                )
            }
        }

        // Return fail if any validationExceptions were caught
        validationExceptions?.let {
            return when {
                it.size == 1 -> ValidationFail(it.first())
                else -> ValidationFail(it)
            }
        }

        if (isChanged) {
            if (version > objectToChange.lastVersion) {
                objectToChange.lastVersion = version
            }
        }

        // Apply the new values now all validations have been accepted
        objectToChange.values = newValueList

        // Nothing skipped out so must be a success
        return Success(version)
    } catch (e: Throwable) {
        return ServerFail(e.toString())
    }
}
