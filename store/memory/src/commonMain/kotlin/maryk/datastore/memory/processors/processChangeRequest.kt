package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.ValueWriter
import maryk.core.processors.datastore.writeIncMapAdditionsToStorage
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.writeTypedValueToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.EmptyValueItems
import maryk.core.values.Values
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.processors.changers.createCountUpdater
import maryk.datastore.memory.processors.changers.deleteByReference
import maryk.datastore.memory.processors.changers.getCurrentIncMapKey
import maryk.datastore.memory.processors.changers.getList
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.processors.changers.setListValue
import maryk.datastore.memory.processors.changers.setValue
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.Update
import maryk.lib.extensions.compare.compareTo

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    storeAction: ChangeStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateSendChannel: SendChannel<Update<*, *>>
) {
    val changeRequest = storeAction.request
    val version = storeAction.version

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(changeRequest.dataModel) as DataStore<DM, P>

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        objectChanges@ for (objectChange in changeRequest.objects) {
            val index = dataStore.records.binarySearch { it.key.compareTo(objectChange.key) }
            val status: IsChangeResponseStatus<DM> = if (index >= 0) {
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

                applyChanges(
                    changeRequest.dataModel,
                    dataStore,
                    objectToChange,
                    objectChange.changes,
                    version,
                    dataStore.keepAllVersions,
                    updateSendChannel
                )
            } else {
                DoesNotExist(objectChange.key)
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
private suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> applyChanges(
    dataModel: DM,
    dataStore: DataStore<DM, P>,
    objectToChange: DataRecord<DM, P>,
    changes: List<IsChange>,
    version: HLC,
    keepAllVersions: Boolean,
    updateSendChannel: SendChannel<Update<*, *>>
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

        val outChanges = mutableListOf<IsChange>()

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
                                    @Suppress("UNCHECKED_CAST")
                                    val mapDefinition = reference.propertyDefinition as? IsMapDefinition<Any, Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsMapDefinition for Map change")

                                    @Suppress("UNCHECKED_CAST")
                                    val mapReference = reference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *>

                                    // Delete all existing values in placeholder
                                    val hadPrevValue =
                                        deleteByReference(newValueList, mapReference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    mapDefinition.validateWithRef(
                                        if (hadPrevValue) mapOf() else null,
                                        value as Map<Any, Any>
                                    ) { mapReference }

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeMapToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        mapDefinition,
                                        value
                                    )
                                }
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val listDefinition = reference.propertyDefinition as? IsListDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsListDefinition for List change")

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(newValueList, reference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    listDefinition.validateWithRef(
                                        if (hadPrevValue) listOf() else null,
                                        value as List<Any>
                                    ) { reference as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }

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
                                    @Suppress("UNCHECKED_CAST")
                                    val setDefinition = reference.propertyDefinition as? IsSetDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsSetDefinition for Set change")

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(newValueList, reference, version, keepAllVersions)

                                    @Suppress("UNCHECKED_CAST")
                                    setDefinition.validateWithRef(
                                        if (hadPrevValue) setOf() else null,
                                        value as Set<Any>
                                    ) { reference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }

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
                                    if (reference.propertyDefinition !is IsMultiTypeDefinition<*, *, *>) {
                                        throw TypeException("Expected a Reference to IsMultiTypeDefinition for TypedValue change")
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    val multiTypeDefinition =
                                        reference.propertyDefinition as IsMultiTypeDefinition<MultiTypeEnum<Any>, Any, IsPropertyContext>
                                    @Suppress("UNCHECKED_CAST")
                                    val multiTypeReference = reference as IsPropertyReference<TypedValue<MultiTypeEnum<Any>, Any>, IsPropertyDefinition<TypedValue<MultiTypeEnum<Any>, Any>>, *>

                                    // Previous value to find
                                    var prevValue: TypedValue<MultiTypeEnum<Any>, *>? = null
                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(
                                        newValueList,
                                        multiTypeReference,
                                        version,
                                        keepAllVersions
                                    ) { _, prevTypedValue ->
                                        prevValue = prevTypedValue
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    multiTypeDefinition.validateWithRef(
                                        if (hadPrevValue) prevValue else null,
                                        value as TypedValue<MultiTypeEnum<Any>, Any>
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
                                    // Process any reference containing values
                                    if (reference.propertyDefinition !is IsEmbeddedValuesDefinition<*, *, *>) {
                                        throw TypeException("Expected a Reference to IsEmbeddedValuesDefinition for Values change")
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    val valuesDefinition = reference.propertyDefinition as IsEmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, IsPropertyContext>
                                    @Suppress("UNCHECKED_CAST")
                                    val valuesReference = reference as IsPropertyReference<Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>, IsPropertyDefinition<Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>, *>

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(
                                        newValueList,
                                        valuesReference,
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
                                            if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null) {
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
                                                        reference.parentReference as IsPropertyReference<*, *, *>,
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
                    is IncMapChange -> {
                        @Suppress("UNCHECKED_CAST")
                        for (valueChange in change.valueChanges) {
                            val incMapReference = valueChange.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>
                            val incMapDefinition = incMapReference.propertyDefinition

                            valueChange.addValues?.let { addValues ->
                                createValidationUmbrellaException({ incMapReference }) { addException ->
                                    for ((index, value) in addValues.withIndex()) {
                                        try {
                                            incMapDefinition.valueDefinition.validateWithRef(null, value) { incMapDefinition.addIndexRef(index, incMapReference) }
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }
                                    }
                                }

                                val currentIncMapKey = getCurrentIncMapKey(
                                    newValueList,
                                    incMapReference
                                )

                                val addedKeys = writeIncMapAdditionsToStorage(
                                    currentIncMapKey,
                                    createValueWriter(newValueList, version, keepAllVersions),
                                    incMapDefinition.definition,
                                    addValues
                                )

                                // Add increment keys to out changes so requester knows at what key values where added to
                                if (addedKeys.isNotEmpty()) {
                                    setChanged(true)
                                    val addition = outChanges.find { it is IncMapAddition } as IncMapAddition?
                                        ?: IncMapAddition(additions = mutableListOf()).also { outChanges.add(it) }
                                    (addition.additions as MutableList<IncMapKeyAdditions<*, *>>).add(
                                        IncMapKeyAdditions(incMapReference, addedKeys, valueChange.addValues)
                                    )
                                }

                                createCountUpdater(
                                    newValueList,
                                    valueChange.reference,
                                    version,
                                    addValues.size,
                                    keepAllVersions
                                ) {
                                    incMapDefinition.validateSize(it) { incMapReference }
                                }
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
                    AlreadyExistsException(ref, ue.key)
                )
            }
        }

        // Return fail if any validationExceptions were caught
        validationExceptions?.let {
            return when (it.size) {
                1 -> ValidationFail(it.first())
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
            } else if (oldValue == null || !newValue.contentEquals(oldValue)) {
                dataStore.addToIndex(objectToChange, it.toReferenceStorageByteArray(), newValue, version, oldValue)
            }
        }

        // Apply the new values now all validations have been accepted
        objectToChange.values = newValueList

        updateSendChannel.send(
            Update.Change(dataModel, objectToChange.key, version, changes)
        )

        // Nothing skipped out so must be a success
        return ChangeSuccess(version.timestamp, outChanges)
    } catch (e: Throwable) {
        return ServerFail(e.toString(), e)
    }
}

/**
 * Create a ValueWriter to [newValueList] at [version]
 * Use [keepAllVersions] at true to keep all past versions
 */
private fun createValueWriter(
    newValueList: MutableList<DataRecordNode>,
    version: HLC,
    keepAllVersions: Boolean
): ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, mapValue ->
    val valueIndex = newValueList.binarySearch {
        it.reference.compareTo(qualifier)
    }
    setValueAtIndex(
        newValueList, valueIndex, qualifier, mapValue, version, keepAllVersions
    )
}
