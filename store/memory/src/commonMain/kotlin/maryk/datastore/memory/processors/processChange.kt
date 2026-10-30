package maryk.datastore.memory.processors

import maryk.datastore.memory.records.haveSameStoredValue

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.emptyValues
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.ValueWriter
import maryk.core.processors.datastore.writeIncMapAdditionsToStorage
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.writeTypedValueToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.AnyPropertyReference
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
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.toListIndex
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.IsIndexUpdate
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.memory.processors.changers.createCountUpdater
import maryk.datastore.memory.processors.changers.deleteByReference
import maryk.datastore.memory.processors.changers.getCurrentIncMapKey
import maryk.datastore.memory.processors.changers.getList
import maryk.datastore.memory.processors.changers.getValue
import maryk.datastore.memory.processors.changers.getValueAtIndex
import maryk.datastore.memory.processors.changers.setListValue
import maryk.datastore.memory.processors.changers.setValue
import maryk.datastore.memory.processors.changers.setValueAtIndex
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DataStore
import maryk.datastore.memory.records.DeletedValue
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.hasActiveStoredType
import maryk.datastore.shared.diffIndexValues
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.FlowUpdateEmitter
import maryk.datastore.shared.updates.Update
import maryk.lib.extensions.compare.compareTo

/**
 * Apply [changes] to a specific object at [key] and record them as [version]
 */
internal suspend fun <DM : IsRootDataModel> processChange(
    dataStore: DataStore<DM>,
    dataModel: DM,
    key: Key<DM>,
    lastVersion: ULong?,
    changes: List<IsChange>,
    version: HLC,
    updateSharedFlow: FlowUpdateEmitter,
    advanceVersionOnNoOp: Boolean = false,
): IsChangeResponseStatus<DM> {
    val index = dataStore.records.binarySearch { it.key compareTo key }

    return if (index < 0) {
        DoesNotExist(key)
    } else {
        val objectToChange = dataStore.records[index]

        // Check if version is within range
        if (lastVersion != null && objectToChange.lastVersion.compareTo(lastVersion) != 0) {
            ValidationFail(
                listOf(
                    InvalidValueException(
                        null,
                        "Version of object was different than given: $lastVersion < ${objectToChange.lastVersion}"
                    )
                )
            )
        } else {
            processChangeIntoStore(
                dataModel,
                dataStore,
                objectToChange,
                changes,
                version,
                dataStore.keepAllVersions,
                updateSharedFlow,
                advanceVersionOnNoOp,
            )
        }
    }
}

private suspend fun <DM : IsRootDataModel> processChangeIntoStore(
    dataModel: DM,
    dataStore: DataStore<DM>,
    objectToChange: DataRecord<DM>,
    changes: List<IsChange>,
    version: HLC,
    keepAllVersions: Boolean,
    updateSharedFlow: FlowUpdateEmitter,
    advanceVersionOnNoOp: Boolean,
): IsChangeResponseStatus<DM> {
    try {
        var validationExceptions: MutableList<ValidationException>? = null

        fun addValidationFail(ve: ValidationException) {
            if (validationExceptions == null) {
                validationExceptions = mutableListOf()
            }
            validationExceptions.add(ve)
        }

        fun addUniqueValidationFail(ue: UniqueException) {
            var index = 0
            val ref = dataModel.getPropertyReferenceByStorageBytes(
                ue.reference.size,
                { ue.reference[index++] }
            )
            addValidationFail(AlreadyExistsException(ref, ue.key))
        }

        val newValueList = objectToChange.values.toMutableList()
        val wasSoftDeleted = objectToChange.isDeleted(null)

        fun hasActiveType(reference: AnyPropertyReference) = hasActiveStoredType(reference) { parent ->
            when (val value = getValue<Any>(newValueList, parent.toStorageByteArray())?.value) {
                is TypedValue<*, *> -> value.type.index
                is TypeEnum<*> -> value.index
                else -> null
            }
        }

        fun requireActiveType(reference: AnyPropertyReference) {
            if (!hasActiveType(reference)) {
                throw RequestException("Property '${reference.completeName}' can only be changed in its active type. Set the multi type parent first.")
            }
        }

        var isChanged = false
        val setChanged = { didChange: Boolean -> if (didChange) isChanged = true }

        val outChanges = mutableListOf<IsChange>()

        val oldIndexValues = dataModel.Meta.indexes?.map {
            it.toStorageByteArraysForIndex(objectToChange, objectToChange.key.bytes)
        }

        for (change in changes) {
            try {
                when (change) {
                    is ObjectSoftDeleteChange -> {
                        val wasDeleted = getValue<Boolean>(newValueList, objectSoftDeleteQualifier, version)?.value == true
                        if (wasDeleted == change.isDeleted) {
                            continue
                        }

                        val valueIndex = newValueList.binarySearch {
                            it.reference compareTo objectSoftDeleteQualifier
                        }
                        setValueAtIndex(
                            newValueList,
                            valueIndex,
                            objectSoftDeleteQualifier,
                            change.isDeleted,
                            version,
                            keepAllVersions
                        )
                        setChanged(true)
                    }
                    is Check -> {
                        // Use the statement's staged state, not the original record
                        // or a collection count stored at its root qualifier.
                        val completeValues = if (change.referenceValuePairs.any { it.reference.comparablePropertyDefinition !is IsSimpleValueDefinition<*, *> }) {
                            val currentRecord = objectToChange.copy(values = newValueList.map { node ->
                                if (node is DataRecordHistoricValues<*>) (node.toAdd as DataRecordNode?) ?: node else node
                            })
                            dataModel.recordToValueWithMeta(null, null, currentRecord)?.values
                        } else null
                        for ((reference, expected) in change.referenceValuePairs) {
                            val actual = if (!hasActiveType(reference)) null
                                else if (reference.comparablePropertyDefinition is IsSimpleValueDefinition<*, *>) getValue<Any>(newValueList, reference.toStorageByteArray())?.value
                                else completeValues?.get(reference)
                            if (actual != expected) addValidationFail(InvalidValueException(reference, expected.toString()))
                        }
                    }
                    is Change -> {
                        for (pair in change.referenceValuePairs) {
                            @Suppress("UNCHECKED_CAST")
                            val reference = pair.reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                            if (pair.value == null && !hasActiveType(reference)) continue
                            requireActiveType(reference)
                            if (pair.value != null && reference is TypedValueReference<*, *, *> &&
                                reference.comparablePropertyDefinition is IsSimpleValueDefinition<*, *>) {
                                throw RequestException("Simple type values must be changed through the multi type parent.")
                            }
                            when (val value = pair.value) {
                                null -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val deletionReference = reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
                                    deleteByReference(newValueList, deletionReference, version, keepAllVersions) { _, previousValue ->
                                        try {
                                            deletionReference.propertyDefinition.validateWithRef(
                                                previousValue = previousValue,
                                                newValue = null,
                                                refGetter = { deletionReference }
                                            )

                                            // Extra validations based on reference type
                                            when (reference) {
                                                is MapKeyReference<*, *, *> -> throw RequestException("Not allowed to delete Map key, delete value instead")
                                                is MapAnyValueReference<*, *, *> -> throw RequestException("Not allowed to delete Map with any key reference, delete by map reference instead")
                                                is ListAnyItemReference<*, *> -> throw RequestException("Not allowed to delete List with any item reference, delete by list reference instead")
                                            }
                                        } catch (e: ValidationException) {
                                            addValidationFail(e)
                                        }
                                    }.also(setChanged)
                                }
                                is Map<*, *> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val mapDefinition = reference.propertyDefinition as? IsMapDefinition<Any, Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsMapDefinition for Map change")

                                    @Suppress("UNCHECKED_CAST")
                                    val mapReference = reference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *>

                                    val referenceBytes = reference.toStorageByteArray()
                                    val currentValues = getCurrentValuesForPrefix(newValueList, referenceBytes)
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    mapDefinition.validateWithRef(
                                        if (hadPrevValue) mapOf() else null,
                                        value as Map<Any, Any>
                                    ) { mapReference }

                                    val expectedValues = mutableListOf<Pair<ByteArray, Any?>>()
                                    val collectingWriter: ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, mapValue ->
                                        expectedValues.add(qualifier to mapValue)
                                    }
                                    writeMapToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        collectingWriter,
                                        mapDefinition,
                                        value
                                    )
                                    if (hasSameStorageValues(currentValues, expectedValues)) {
                                        continue
                                    }

                                    checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)

                                    // Delete all existing values in placeholder
                                    deleteByReference(newValueList, mapReference, version, keepAllVersions)

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeMapToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        mapDefinition,
                                        value
                                    )
                                    setChanged(true)
                                }
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val listDefinition = reference.propertyDefinition as? IsListDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsListDefinition for List change")

                                    val referenceBytes = reference.toStorageByteArray()
                                    val currentValues = getCurrentValuesForPrefix(newValueList, referenceBytes)
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    listDefinition.validateWithRef(
                                        if (hadPrevValue) listOf() else null,
                                        value as List<Any>
                                    ) { reference as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }

                                    val expectedValues = mutableListOf<Pair<ByteArray, Any?>>()
                                    val collectingWriter: ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, mapValue ->
                                        expectedValues.add(qualifier to mapValue)
                                    }
                                    writeListToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        collectingWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                    if (hasSameStorageValues(currentValues, expectedValues)) {
                                        continue
                                    }

                                    checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)

                                    // Delete all existing values in placeholder
                                    deleteByReference(newValueList, reference, version, keepAllVersions)

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeListToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                    setChanged(true)
                                }
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val setDefinition = reference.propertyDefinition as? IsSetDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsSetDefinition for Set change")

                                    val referenceBytes = reference.toStorageByteArray()
                                    val currentValues = getCurrentValuesForPrefix(newValueList, referenceBytes)
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    setDefinition.validateWithRef(
                                        if (hadPrevValue) setOf() else null,
                                        value as Set<Any>
                                    ) { reference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }

                                    val expectedValues = mutableListOf<Pair<ByteArray, Any?>>()
                                    val collectingWriter: ValueWriter<IsPropertyDefinition<*>> = { _, qualifier, _, mapValue ->
                                        expectedValues.add(qualifier to mapValue)
                                    }
                                    writeSetToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        collectingWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                    if (hasSameStorageValues(currentValues, expectedValues)) {
                                        continue
                                    }

                                    checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)

                                    // Delete all existing values in placeholder
                                    deleteByReference(newValueList, reference, version, keepAllVersions)

                                    val valueWriter = createValueWriter(newValueList, version, keepAllVersions)

                                    writeSetToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                    setChanged(true)
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

                                    checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)

                                    writeTypedValueToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
                                    setChanged(true)
                                }
                                is Values<*> -> {
                                    // Process any reference containing values
                                    if (reference.propertyDefinition !is IsEmbeddedValuesDefinition<*, *>) {
                                        throw TypeException("Expected a Reference to IsEmbeddedValuesDefinition for Values change")
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    val valuesDefinition = reference.propertyDefinition as IsEmbeddedValuesDefinition<IsValuesDataModel, IsPropertyContext>
                                    @Suppress("UNCHECKED_CAST")
                                    val valuesReference = reference as IsPropertyReference<Values<IsValuesDataModel>, IsPropertyDefinition<Values<IsValuesDataModel>>, *>

                                    // Delete all existing values in placeholder
                                    val hadPrevValue = deleteByReference(
                                        newValueList,
                                        valuesReference,
                                        version,
                                        keepAllVersions
                                    )

                                    @Suppress("UNCHECKED_CAST")
                                    valuesDefinition.validateWithRef(
                                        if (hadPrevValue) valuesDefinition.dataModel.emptyValues() else null,
                                        value as Values<IsValuesDataModel>
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

                                    checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)

                                    value.writeToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter
                                    )
                                    setChanged(true)
                                }
                                else -> {
                                    setValue(
                                        newValueList, reference, value, version, keepAllVersions
                                    ) { _, previousValue ->
                                        if (previousValue == null) {
                                            // Check if parent exists before trying to change
                                            if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null) {
                                                getValue<Any>(
                                                    newValueList,
                                                    reference.parentReference!!.toStorageByteArray()
                                                ) ?: throw RequestException("Property '${reference.completeName}' can only be changed if parent value exists. Set the parent value with this value.")
                                            }

                                            // Extra validations based on reference type
                                            checkParentReference(reference, newValueList, version, keepAllVersions, ::addValidationFail)
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
                    is ListChange -> {
                        for (listChange in change.listValueChanges) {
                            requireActiveType(listChange.reference)
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
                                    list.add(index.toListIndex(), value)
                                }
                            }
                            listChange.addValuesToEnd?.let {
                                for (value in it) {
                                    list.add(value)
                                }
                            }

                            if ((originalList == null && list.isEmpty()) || (originalList != null && list == originalList)) {
                                continue
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
                            requireActiveType(setChange.reference)
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
                            requireActiveType(valueChange.reference)
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
                addUniqueValidationFail(ue)
            }
        }

        val isSoftDeleted = getValue<Boolean>(newValueList, objectSoftDeleteQualifier)?.value == true
        val oldUniqueValues = if (wasSoftDeleted) emptyMap() else collectUniqueValues(dataModel, objectToChange.values)
        val newUniqueValues = if (isSoftDeleted) emptyMap() else collectUniqueValues(dataModel, newValueList)
        for (uniqueValue in newUniqueValues.values) {
            try {
                dataStore.validateUniqueNotExists(uniqueValue, objectToChange)
            } catch (ue: UniqueException) {
                addUniqueValidationFail(ue)
            }
        }

        // Return fail if any validationExceptions were caught
        validationExceptions?.let {
            return when (it.size) {
                1 -> ValidationFail(it.first())
                else -> ValidationFail(it)
            }
        }

        if (!isChanged) {
            if (advanceVersionOnNoOp && version > objectToChange.lastVersion) {
                objectToChange.lastVersion = version
            }
            return ChangeSuccess(objectToChange.lastVersion.timestamp, outChanges)
        }

        if (version > objectToChange.lastVersion) {
            objectToChange.lastVersion = version
        }
        dataStore.addToUpdateHistory(version, objectToChange.key.bytes)

        for ((reference, oldValue) in oldUniqueValues) {
            if (!haveSameStoredValue(newUniqueValues[reference]?.value, oldValue.value)) {
                dataStore.removeFromUniqueIndex(objectToChange, oldValue.reference, oldValue.value, version)
            }
        }
        for ((reference, newValue) in newUniqueValues) {
            if (!haveSameStoredValue(oldUniqueValues[reference]?.value, newValue.value)) {
                dataStore.addToUniqueIndex(objectToChange, newValue.reference, newValue.value, version)
            }
        }

        var indexUpdates: MutableList<IsIndexUpdate>? = null

        // Commit all changes in historic nodes
        for (node in newValueList) {
            node.commit()
        }

        // Apply the new values now all validations have been accepted
        objectToChange.values = newValueList

        // Process indexes
        dataModel.Meta.indexes?.forEachIndexed { index, it ->
            if (indexUpdates == null) {
                indexUpdates = mutableListOf()
            }

            val oldValues = if (wasSoftDeleted) emptyList() else oldIndexValues?.get(index).orEmpty()
            val newValues = if (isSoftDeleted) emptyList() else {
                it.toStorageByteArraysForIndex(objectToChange, objectToChange.key.bytes)
            }

            val (removed, added) = diffIndexValues(oldValues, newValues)

            if (removed.size == 1 && added.size == 1) {
                val oldValue = removed.first()
                val newValue = added.first()
                dataStore.addToIndex(objectToChange, it.referenceStorageByteArray.bytes, newValue, version, oldValue)
                indexUpdates.add(IndexUpdate(it.referenceStorageByteArray, Bytes(newValue), Bytes(oldValue)))
            } else {
                removed.forEach { oldValue ->
                    dataStore.removeFromIndex(objectToChange, it.referenceStorageByteArray.bytes, version, oldValue)
                    indexUpdates.add(IndexDelete(it.referenceStorageByteArray, Bytes(oldValue)))
                }
                added.forEach { newValue ->
                    dataStore.addToIndex(objectToChange, it.referenceStorageByteArray.bytes, newValue, version)
                    indexUpdates.add(IndexUpdate(it.referenceStorageByteArray, Bytes(newValue), null))
                }
            }
        }

        indexUpdates?.let {
            if (it.isNotEmpty()) {
                outChanges += IndexChange(it)
            }
        }

        updateSharedFlow(
            Update.Change(dataModel, objectToChange.key, version.timestamp, changes + outChanges)
        )

        // Nothing skipped out so must be a success
        return ChangeSuccess(version.timestamp, outChanges)
    } catch (e: Throwable) {
        e.rethrowIfFatal()
        return ServerFail(e.toString(), e)
    }
}

private fun checkParentReference(
    reference: IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
    newValueList: MutableList<DataRecordNode>,
    version: HLC,
    keepAllVersions: Boolean,
    addValidationFail: (ve: ValidationException) -> Unit,
) {
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

/** Resolve unique descendants from the complete staged state, including complex replacements. */
private fun collectUniqueValues(
    dataModel: IsRootDataModel,
    values: List<DataRecordNode>,
): Map<Bytes, DataRecordValue<Comparable<Any>>> = buildMap {
    for (index in values.indices) {
        val value = getValueAtIndex<Any>(values, index) ?: continue
        if (value.reference.contentEquals(objectSoftDeleteQualifier)) continue
        var offset = 0
        val definition = dataModel.getPropertyReferenceByStorageBytes(value.reference.size, reader = { value.reference[offset++] })
            .comparablePropertyDefinition
        if (definition is IsComparableDefinition<*, *> && definition.unique) {
            @Suppress("UNCHECKED_CAST")
            put(Bytes(value.reference), value as DataRecordValue<Comparable<Any>>)
        }
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
        it.reference compareTo qualifier
    }
    setValueAtIndex(
        newValueList, valueIndex, qualifier, mapValue, version, keepAllVersions
    )
}

private fun getCurrentValuesForPrefix(
    values: List<DataRecordNode>,
    referenceBytes: ByteArray
): List<Pair<ByteArray, Any?>> {
    val startIndex = values.binarySearch {
        it.reference compareTo referenceBytes
    }.let { if (it < 0) it * -1 - 1 else it }
    val currentValues = mutableListOf<Pair<ByteArray, Any?>>()

    for (index in startIndex until values.size) {
        val node = values[index]
        if (!node.reference.startsWith(referenceBytes)) break

        val currentValue = when (node) {
            is DataRecordValue<*> -> node.value
            is DataRecordHistoricValues<*> -> {
                val latest = node.toAdd ?: node.history.lastOrNull()
                when (latest) {
                    is DataRecordValue<*> -> latest.value
                    else -> null
                }
            }
            is DeletedValue<*> -> null
        }

        if (currentValue != null) {
            currentValues.add(node.reference to currentValue)
        }
    }

    return currentValues
}

private fun hasSameStorageValues(
    currentValues: List<Pair<ByteArray, Any?>>,
    expectedValues: List<Pair<ByteArray, Any?>>
): Boolean {
    if (currentValues.size != expectedValues.size) return false

    val sortedCurrent = currentValues.sortedWith { left, right -> left.first.compareTo(right.first) }
    val sortedExpected = expectedValues.sortedWith { left, right -> left.first.compareTo(right.first) }

    for (index in sortedCurrent.indices) {
        val current = sortedCurrent[index]
        val expected = sortedExpected[index]
        if (!current.first.contentEquals(expected.first)) return false
        if (!valuesEqual(current.second, expected.second)) return false
    }

    return true
}

private fun valuesEqual(left: Any?, right: Any?): Boolean =
    when {
        left is ByteArray && right is ByteArray -> left.contentEquals(right)
        else -> haveSameStoredValue(left, right)
    }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (prefix.size > this.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}
