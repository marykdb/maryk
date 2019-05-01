package maryk.datastore.memory.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.ValueWriter
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.writeTypedValueToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.EmptyValueItems
import maryk.core.values.Values
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.processors.changers.createCountUpdater
import maryk.datastore.memory.processors.changers.deleteByReference
import maryk.datastore.memory.processors.changers.getList
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.processors.changers.setListValue
import maryk.datastore.memory.processors.changers.setValue
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.memory.records.index.UniqueException
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matches
import maryk.lib.time.Instant

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    storeAction: ChangeStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val changeRequest = storeAction.request
    val version = Instant.getCurrentEpochTimeInMillis().toULong()

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        objectChanges@ for (objectChange in changeRequest.objects) {
            val index = dataStore.records.binarySearch { it.key.compareTo(objectChange.key) }
            val objectToChange = dataStore.records[index]

            val lastVersion = objectChange.lastVersion
            // Check if version is within range
            if (lastVersion != null && objectToChange.lastVersion.compareTo(lastVersion) != 0) {
                statuses.add(
                    ValidationFail(
                        listOf(
                            InvalidValueException(
                                null,
                                "Version of object was different than given: ${objectChange.lastVersion} < ${objectToChange.lastVersion}"
                            )
                        )
                    )
                )
                continue@objectChanges
            }

            val status: IsChangeResponseStatus<DM> = when {
                index > -1 -> {
                    applyChanges(
                        changeRequest.dataModel,
                        dataStore,
                        objectToChange,
                        objectChange.changes,
                        version,
                        dataStore.keepAllVersions
                    )
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
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> applyChanges(
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

        var uniquesToIndex: MutableMap<DataRecordValue<Comparable<Any>>, Any?>? = null

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
                            when (value) {
                                is Map<*, *> -> {
                                    if (reference !is MapReference<*, *, *>) {
                                        throw TypeException("Expected a MapReference for a map")
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    val mapReference = reference as MapReference<Any, Any, IsPropertyContext>

                                    // Delete all existing values in placeholder
                                    val hadPrevValue =
                                        deleteByReference(newValueList, mapReference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    reference.propertyDefinition.definition.validateWithRef(
                                        if (hadPrevValue) mapOf() else null,
                                        value as Map<Any, Any>
                                    ) { mapReference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeMapToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                }
                                is List<*> -> {
                                    if (reference !is ListReference<*, *>) {
                                        throw TypeException("Expected a ListReference for a List")
                                    }

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(newValueList, reference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    (reference as ListReference<Any, IsPropertyContext>).propertyDefinition.definition.validateWithRef(
                                        if (hadPrevValue) listOf() else null,
                                        value as List<Any>
                                    ) { reference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeListToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                }
                                is Set<*> -> {
                                    if (reference !is SetReference<*, *>) {
                                        throw TypeException("Expected a SetReference for a Set")
                                    }

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(newValueList, reference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    (reference as SetReference<Any, IsPropertyContext>).propertyDefinition.definition.validateWithRef(
                                        if (hadPrevValue) setOf() else null,
                                        value as Set<Any>
                                    ) { reference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeSetToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                }
                                is TypedValue<*, *> -> {
                                    if (reference !is MultiTypePropertyReference<*, *, *, *>) {
                                        throw TypeException("Expected a MultiTypePropertyReference for a typedValue")
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    val multiTypeReference =
                                        reference as MultiTypePropertyReference<IndexedEnum, Any, *, *>
                                    @Suppress("UNCHECKED_CAST")
                                    val multiTypeDefinition =
                                        multiTypeReference.propertyDefinition.definition as MultiTypeDefinition<IndexedEnum, IsPropertyContext>

                                    // Previous value to find
                                    var prevValue: TypedValue<*, *>? = null
                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(
                                        newValueList,
                                        multiTypeReference,
                                        version,
                                        keepAllVersions
                                    ) { _, prevTypedValue ->
                                        prevValue = prevTypedValue
                                    }

                                    multiTypeDefinition.validateWithRef(
                                        if (hadPrevValue) prevValue else null,
                                        value
                                    ) { multiTypeReference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeTypedValueToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                }
                                is Values<*, *> -> {
                                    if (reference !is EmbeddedValuesPropertyRef<*, *, *>) {
                                        throw TypeException("Expected a EmbeddedValuesPropertyRef for Values")
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    val valuesReference =
                                        reference as EmbeddedValuesPropertyRef<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, IsPropertyContext>
                                    val valuesDefinition = valuesReference.propertyDefinition.definition

                                    // Delete all existing values in placeholder
                                    @Suppress("UNCHECKED_CAST")
                                    val hadPrevValue = deleteByReference(
                                        newValueList,
                                        valuesReference as IsPropertyReference<Values<*, *>, IsPropertyDefinition<Values<*, *>>, *>,
                                        version,
                                        keepAllVersions
                                    )

                                    @Suppress("UNCHECKED_CAST")
                                    valuesDefinition.validateWithRef(
                                        if (hadPrevValue) valuesDefinition.dataModel.values(null) { EmptyValueItems } else null,
                                        value as Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>
                                    ) { valuesReference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    // Write complex values existence indicator
                                    // Write parent value with Unit so it knows this one is not deleted. So possible lingering old types are not read.
                                    valueWriter(
                                        Embed,
                                        reference.toStorageByteArray(),
                                        valuesDefinition,
                                        Unit
                                    )

                                    value.writeToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter
                                    )
                                }
                                else -> {
                                    setValue(
                                        newValueList, reference, value, version, keepAllVersions
                                    ) { dataRecordValue, previousValue ->
                                        val definition = reference.comparablePropertyDefinition
                                        if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                                            @Suppress("UNCHECKED_CAST")
                                            val comparableValue = dataRecordValue as DataRecordValue<Comparable<Any>>
                                            dataStore.validateUniqueNotExists(comparableValue, objectToChange)
                                            when (uniquesToIndex) {
                                                null -> uniquesToIndex = mutableMapOf(comparableValue to previousValue)
                                                else -> uniquesToIndex!![comparableValue] = previousValue
                                            }
                                        }

                                        if (previousValue == null) {
                                            // Check if parent exists before trying to change
                                            if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *>) {
                                                getValue<Any>(
                                                    newValueList,
                                                    reference.parentReference!!.toStorageByteArray()
                                                ) ?: throw RequestException("Property '${reference.completeName}' can only be changed if parent value exists. Set the parent value with this value.")
                                            }

                                            // Extra validations based on reference type
                                            when (reference) {
                                                is ListItemReference<*, *> -> throw RequestException("ListItem can only be changed if it exists. To add a new one use ListChange.")
                                                is MapValueReference<*, *, *> -> {
                                                    try {
                                                        @Suppress("UNCHECKED_CAST")
                                                        val mapDefinition =
                                                            reference.mapDefinition as IsMapDefinition<Any, Any, IsPropertyContext>
                                                        @Suppress("UNCHECKED_CAST")
                                                        mapDefinition.keyDefinition.validateWithRef(
                                                            reference.key,
                                                            reference.key
                                                        ) {
                                                            mapDefinition.keyRef(
                                                                reference.key,
                                                                reference.parentReference as MapReference<Any, Any, IsPropertyContext>
                                                            )
                                                        }
                                                    } catch (e: ValidationException) {
                                                        addValidationFail(e)
                                                    }

                                                    createCountUpdater(
                                                        newValueList,
                                                        reference.parentReference as IsPropertyReference<out Map<*, *>, IsPropertyDefinition<out Map<*, *>>, out Any>,
                                                        version,
                                                        1,
                                                        keepAllVersions
                                                    ) {
                                                        @Suppress("UNCHECKED_CAST")
                                                        (reference as MapValueReference<Any, Any, IsPropertyContext>).mapDefinition.validateSize(it) { reference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *> }
                                                    }
                                                }
                                                is SetItemReference<*, *> -> throw RequestException("Not allowed to add with a Set Item reference, use SetChange instead")
                                                is MapKeyReference<*, *, *> -> throw RequestException("Not allowed to add with a Map key, use Map value instead")
                                                is MapAnyValueReference<*, *, *> -> throw RequestException("Not allowed to add Map with any key reference")
                                                is ListAnyItemReference<*, *> -> throw RequestException("Not allowed to add List with any item reference")
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

                                    // Extra validations based on reference type
                                    when (ref) {
                                        is MapValueReference<*, *, *> -> {
                                            createCountUpdater(
                                                newValueList,
                                                ref.parentReference as IsPropertyReference<out Map<*, *>, IsPropertyDefinition<out Map<*, *>>, out Any>,
                                                version,
                                                -1,
                                                keepAllVersions
                                            ) {
                                                @Suppress("UNCHECKED_CAST")
                                                (ref as MapValueReference<Any, Any, IsPropertyContext>).mapDefinition.validateSize(it) { ref as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *> }
                                            }
                                        }
                                        is SetItemReference<*, *> -> {
                                            createCountUpdater(
                                                newValueList,
                                                ref.parentReference as IsPropertyReference<out Set<Any>, IsPropertyDefinition<out Set<Any>>, out Any>,
                                                version,
                                                -1,
                                                keepAllVersions
                                            ) {
                                                @Suppress("UNCHECKED_CAST")
                                                (ref as SetItemReference<Any, IsPropertyContext>).setDefinition.validateSize(it) { ref as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }
                                            }
                                        }
                                        is ListItemReference<*, *> -> {
                                            createCountUpdater(
                                                newValueList,
                                                ref.parentReference as IsPropertyReference<out List<Any>, IsPropertyDefinition<out List<Any>>, out Any>,
                                                version,
                                                -1,
                                                keepAllVersions
                                            ) {
                                                @Suppress("UNCHECKED_CAST")
                                                (ref as ListItemReference<Any, IsPropertyContext>).listDefinition.validateSize(it) { ref as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }
                                            }
                                        }
                                        is MapKeyReference<*, *, *> -> throw RequestException("Not allowed to delete Map key, delete value instead")
                                        is MapAnyValueReference<*, *, *> -> throw RequestException("Not allowed to delete Map with any key reference, delete by map reference instead")
                                        is ListAnyItemReference<*, *> -> throw RequestException("Not allowed to delete List with any item reference, delete by list reference instead")
                                    }
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
                            listChange.deleteValues?.let {
                                for (deleteValue in it) {
                                    list.remove(deleteValue)
                                }
                            }
                            listChange.addValuesAtIndex?.let {
                                for ((index, value) in it) {
                                    list.add(index.toInt(), value)
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
                                    parentRefFactory = { (listChange.reference as? IsPropertyReferenceWithParent<Any, *, *, *>)?.parentReference }
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
                                        val setItemRef = setDefinition.itemRef(value, setReference)
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

        uniquesToIndex?.forEach { (value, previousValue) ->
            @Suppress("UNCHECKED_CAST")
            dataStore.addToUniqueIndex(
                objectToChange,
                value.reference,
                value.value,
                version,
                previousValue as Comparable<Any>
            )
        }

        val oldValueList = objectToChange.values

        // Process indices
        dataModel.indices?.forEach {
            val oldValue = it.toStorageByteArrayForIndex(objectToChange, objectToChange.key.bytes)
            // Use switch trick to use less object creation and still be able to get values
            objectToChange.values = newValueList
            val newValue = it.toStorageByteArrayForIndex(objectToChange, objectToChange.key.bytes)
            objectToChange.values = oldValueList

            if (newValue == null) {
                if (oldValue != null) {
                    dataStore.removeFromIndex(objectToChange, it.toReferenceStorageByteArray(), version, oldValue)
                } // else ignore since did not exist
            } else if (oldValue == null || !newValue.matches(oldValue)) {
                dataStore.addToIndex(objectToChange, it.toReferenceStorageByteArray(), newValue, version, oldValue)
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

/**
 * Create a ValueWriter to [newValueList] at [version]
 * Use [keepAllVersions] at true to keep all past versions
 */
private fun createValueWriter(
    newValueList: MutableList<DataRecordNode>,
    version: ULong,
    keepAllVersions: Boolean
): ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, mapValue ->
    val valueIndex = newValueList.binarySearch {
        it.reference.compareTo(qualifier)
    }
    setValueAtIndex(
        newValueList, valueIndex, qualifier, mapValue, version, keepAllVersions
    )
}
