package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.toULong
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.ValueWriter
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
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapChange
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
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.createCountUpdater
import maryk.datastore.rocksdb.processors.helpers.deleteByReference
import maryk.datastore.rocksdb.processors.helpers.getLastVersion
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.rocksdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setValue
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.UniqueException
import maryk.rocksdb.Transaction
import maryk.rocksdb.use

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    storeAction: ChangeStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val changeRequest = storeAction.request
    val version = storeAction.version
    val columnFamilies = dataStore.getColumnFamilies(storeAction.dbIndex)

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        dataStore.db.beginTransaction(dataStore.defaultWriteOptions).use { transaction ->
            objectChanges@ for (objectChange in changeRequest.objects) {
                val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, objectChange.key.bytes, StringBuilder())
                val status: IsChangeResponseStatus<DM> = if (mayExist) {
                    val creationVersion =
                        transaction.get(columnFamilies.keys, dataStore.defaultReadOptions, objectChange.key.bytes)
                            ?.toULong()

                    if (creationVersion != null) {
                        val lastVersionToCheck = objectChange.lastVersion
                        // Check if version is within range
                        if (lastVersionToCheck != null) {
                            val lastVersion = getLastVersion(transaction, columnFamilies, dataStore.defaultReadOptions, objectChange.key)
                            if (lastVersion != lastVersionToCheck) {
                                statuses.add(
                                    ValidationFail(
                                        listOf(
                                            InvalidValueException(null, "Version of object was different than given: ${objectChange.lastVersion} < $lastVersion")
                                        )
                                    )
                                )
                                continue@objectChanges
                            }
                        }
                        applyChanges(
                            changeRequest.dataModel,
                            dataStore,
                            storeAction,
                            transaction,
                            columnFamilies,
                            objectChange.key,
                            objectChange.changes,
                            version,
                            dataStore.keepAllVersions
                        )
                    } else {
                        DoesNotExist(objectChange.key)
                    }
                } else {
                    DoesNotExist(objectChange.key)
                }

                statuses.add(status)
            }
            transaction.commit()
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
 * Apply [changes] to a specific [transaction] and record them as [version]
 * [keepAllVersions] determines if history is kept
 */
@Suppress("UNUSED_PARAMETER")
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> applyChanges(
    dataModel: DM,
    dataStore: RocksDBDataStore,
    storeAction: ChangeStoreAction<DM, P>,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    changes: List<IsChange>,
    version: HLC,
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

//        var uniquesToIndex: MutableMap<DataRecordValue<Comparable<Any>>, Any?>? = null

        var isChanged = false
        val setChanged = { didChange: Boolean -> if (didChange) isChanged = true }

        val outChanges = mutableListOf<IsChange>()

        val versionBytes = HLC.toStorageBytes(version)

        transaction.setSavePoint()

        for (change in changes) {
            try {
                when (change) {
                    is Check -> {
                        for ((reference, value) in change.referenceValuePairs) {
                            transaction.getValue(columnFamilies, dataStore.defaultReadOptions, null, reference.toStorageByteArray(key.bytes)) { b, o, l ->
                                var readIndex = o
                                // Convert stored value excluding defining byte
                                val storedValue = readValue(reference.comparablePropertyDefinition, { b[readIndex++] }) { o + l - readIndex }
                                if (storedValue != value) {
                                    addValidationFail(
                                        InvalidValueException(reference, value.toString())
                                    )
                                }
                            }
                        }
                    }
                    is Change -> {
                        @Suppress("UNUSED_VARIABLE")
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
                                        deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, mapReference, mapReference.toStorageByteArray(), versionBytes) { _, prevValue ->
                                            prevValue != null // Check if parent was deleted
                                        }

                                    @Suppress("UNCHECKED_CAST")
                                    mapDefinition.validateWithRef(
                                        if (hadPrevValue) mapOf() else null,
                                        value as Map<Any, Any>
                                    ) { mapReference }

                                    val valueWriter = createValueWriter(
                                        dataStore, storeAction, transaction, columnFamilies, key, versionBytes
                                    )

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
                                    val hadPrevValue =
                                        deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, reference, reference.toStorageByteArray(), versionBytes) { _, prevValue ->
                                            prevValue != null // Check if parent was deleted
                                        }

                                    @Suppress("UNCHECKED_CAST")
                                    listDefinition.validateWithRef(
                                        if (hadPrevValue) listOf() else null,
                                        value as List<Any>
                                    ) { reference as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }

                                    val valueWriter = createValueWriter(
                                        dataStore, storeAction, transaction, columnFamilies, key, versionBytes
                                    )

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
                                    val hadPrevValue =
                                        deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, reference, reference.toStorageByteArray(), versionBytes) { _, prevValue ->
                                            prevValue != null // Check if parent was deleted
                                        }

                                    @Suppress("UNCHECKED_CAST")
                                    setDefinition.validateWithRef(
                                        if (hadPrevValue) setOf() else null,
                                        value as Set<Any>
                                    ) { reference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }

                                    val valueWriter = createValueWriter(
                                        dataStore, storeAction, transaction, columnFamilies, key, versionBytes
                                    )

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
                                    val hadPrevValue = deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, reference, reference.toStorageByteArray(), versionBytes) { _, prevTypedValue ->
                                        prevValue = prevTypedValue
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    multiTypeDefinition.validateWithRef(
                                        if (hadPrevValue) prevValue else null,
                                        value as TypedValue<MultiTypeEnum<Any>, Any>
                                    ) { multiTypeReference }

                                    val valueWriter = createValueWriter(
                                        dataStore, storeAction, transaction, columnFamilies, key, versionBytes
                                    )

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
                                    val hadPrevValue = deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, reference, reference.toStorageByteArray(), versionBytes) { _, prevValue ->
                                        prevValue != null // Check if parent was deleted
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    valuesDefinition.validateWithRef(
                                        if (hadPrevValue) valuesDefinition.dataModel.values(null) { EmptyValueItems } else null,
                                        value as Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>
                                    ) { valuesReference }

                                    val valueWriter = createValueWriter(
                                        dataStore, storeAction, transaction, columnFamilies, key, versionBytes
                                    )

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
//                                        val definition = reference.comparablePropertyDefinition
//                                        if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
//                                            @Suppress("UNCHECKED_CAST")
//                                            val comparableValue = dataRecordValue as DataRecordValue<Comparable<Any>>
//                                            dataStore.validateUniqueNotExists(comparableValue, objectToChange)
//                                            when (uniquesToIndex) {
//                                                null -> uniquesToIndex = mutableMapOf(comparableValue to previousValue)
//                                                else -> uniquesToIndex!![comparableValue] = previousValue
//                                            }
//                                        }
//
                                    try {
                                        val referenceAsBytes = reference.toStorageByteArray()
                                        val keyAndReference = byteArrayOf(*key.bytes, *referenceAsBytes)

                                        val previousValue = transaction.getValue(columnFamilies, dataStore.defaultReadOptions, null, keyAndReference) { b, o, l ->
                                            var readIndex = o
                                            readValue(reference.comparablePropertyDefinition, { b[readIndex++] }) {
                                                o + l - readIndex
                                            }
                                        }

                                        if (previousValue == null) {
                                            // Check if parent exists before trying to change
                                            if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *>) {
                                                transaction.getValue(columnFamilies, dataStore.defaultReadOptions, null, byteArrayOf(*key.bytes, *reference.parentReference!!.toStorageByteArray())) { b, o, _ ->
                                                    // Check if parent was deleted
                                                    if (b[o] == DELETED_INDICATOR) null else true
                                                } ?: throw RequestException("Property '${reference.completeName}' can only be changed if parent value exists. Set the parent value with this value.")
                                            }

                                            // Extra validations based on reference type
                                            when (reference) {
                                                is ListItemReference<*, *> -> throw RequestException("ListItem can only be changed if it exists. To add a new one use ListChange.")
                                                is MapValueReference<*, *, *> -> {
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

                                                    createCountUpdater(
                                                        transaction,
                                                        columnFamilies,
                                                        dataStore.defaultReadOptions,
                                                        key,
                                                        reference.parentReference as IsPropertyReference<*, *, *>,
                                                        versionBytes,
                                                        1
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

                                        reference.propertyDefinition.validateWithRef(
                                            previousValue = previousValue,
                                            newValue = value,
                                            refGetter = { reference }
                                        )

                                        @Suppress("UNCHECKED_CAST")
                                        val valueBytes = (reference.propertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, NO_TYPE_INDICATOR)

                                        setValue(transaction, columnFamilies, key, referenceAsBytes, versionBytes, valueBytes)
                                        setChanged(true)
                                    } catch (e: ValidationException) {
                                        addValidationFail(e)
                                    }
                                }
                            }
                        }
                    }
                    is Delete -> {
                        for (reference in change.references) {
                            @Suppress("UNCHECKED_CAST")
                            val ref = reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
                            val referenceAsBytes = reference.toStorageByteArray()
                            try {
                                if (reference is TypedValueReference<*, *, *>) {
                                    throw RequestException("Type Reference not allowed for deletes. Use the multi type parent.")
                                }

                                deleteByReference(transaction, columnFamilies, dataStore.defaultReadOptions, key, reference, referenceAsBytes, versionBytes) { _, previousValue ->
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
                                        else -> {}
                                    }
                                }.also(setChanged)
                            } catch (e: ValidationException) {
                                addValidationFail(e)
                            }
                        }
                    }
                    is ListChange -> {
                        TODO("CHANGE LIST CHANGE")
//                        for (listChange in change.listValueChanges) {
//                            val originalList = getList(newValueList, listChange.reference)
//                            val list = originalList?.toMutableList() ?: mutableListOf()
//                            val originalCount = list.size
//                            listChange.deleteValues?.let {
//                                for (deleteValue in it) {
//                                    list.remove(deleteValue)
//                                }
//                            }
//                            listChange.addValuesAtIndex?.let {
//                                for ((index, value) in it) {
//                                    list.add(index.toInt(), value)
//                                }
//                            }
//                            listChange.addValuesToEnd?.let {
//                                for (value in it) {
//                                    list.add(value)
//                                }
//                            }
//
//                            try {
//                                @Suppress("UNCHECKED_CAST")
//                                (listChange.reference as ListReference<Any, *>).propertyDefinition.validate(
//                                    previousValue = originalList,
//                                    newValue = list,
//                                    parentRefFactory = { (listChange.reference as? IsPropertyReferenceWithParent<Any, *, *, *>)?.parentReference }
//                                )
//                            } catch (e: ValidationException) {
//                                addValidationFail(e)
//                            }
//                            setListValue(
//                                newValueList,
//                                listChange.reference,
//                                list,
//                                originalCount,
//                                version,
//                                keepAllVersions
//                            ).also(setChanged)
//                        }
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

                                            val keyAndReference = setItemRef.toStorageByteArray(key.bytes)

                                            // Check previous value and delete it if needed
                                            transaction.getValue(columnFamilies, dataStore.defaultReadOptions, null, keyAndReference) { b, o, _ ->
                                                // Check if parent was deleted
                                                if (b[o] == DELETED_INDICATOR) null else true
                                            } ?: countChange++ // Add 1 because does not exist

                                            @Suppress("UNCHECKED_CAST")
                                            val valueBytes = (setItemRef.propertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, NO_TYPE_INDICATOR)

                                            setValue(transaction, columnFamilies, keyAndReference, versionBytes, valueBytes)
                                            setChanged(true)
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }
                                    }
                                }
                            }

                            createCountUpdater(
                                transaction,
                                columnFamilies,
                                dataStore.defaultReadOptions,
                                key,
                                setChange.reference,
                                versionBytes,
                                countChange
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
//
//                                val currentIncMapKey = getCurrentIncMapKey(
//                                    newValueList,
//                                    incMapReference
//                                )
//
//                                val valueWriter = createValueWriter(
//                                    dataStore, storeAction, transaction, columnFamilies, key, versionBytes
//                                )
//
//                                val addedKeys = writeIncMapAdditionsToStorage(
//                                    currentIncMapKey,
//                                    valueWriter,
//                                    incMapDefinition.definition,
//                                    addValues
//                                )
//
//                                // Add increment keys to out changes so requester knows at what key values where added to
//                                if (addedKeys.isNotEmpty()) {
//                                    setChanged(true)
//                                    val addition = outChanges.find { it is IncMapAddition } as IncMapAddition?
//                                        ?: IncMapAddition(additions = mutableListOf()).also { outChanges.add(it) }
//                                    (addition.additions as MutableList<IncMapKeyAdditions<*, *>>).add(
//                                        IncMapKeyAdditions(incMapReference, addedKeys)
//                                    )
//                                }
//
//                                createCountUpdater(
//                                    transaction,
//                                    columnFamilies,
//                                    dataStore.defaultReadOptions,
//                                    key,
//                                    valueChange.reference,
//                                    versionBytes,
//                                    addValues.size
//                                ) {
//                                    incMapDefinition.validateSize(it) { incMapReference }
//                                }
                                TODO("CHANGE INC MAP")
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
            // Undo snapshot because of validation exceptions
            transaction.rollbackToSavePoint()
            return when {
                it.size == 1 -> ValidationFail(it.first())
                else -> ValidationFail(it)
            }
        }

        if (isChanged) {
            val lastVersion = getLastVersion(transaction, columnFamilies, dataStore.defaultReadOptions, key)
            if (version.timestamp > lastVersion) {
                setLatestVersion(transaction, columnFamilies, key, HLC.toStorageBytes(version))
            }
        }

//        uniquesToIndex?.forEach { (value, previousValue) ->
//            @Suppress("UNCHECKED_CAST")
//            dataStore.addToUniqueIndex(
//                objectToChange,
//                value.reference,
//                value.value,
//                version,
//                previousValue as Comparable<Any>
//            )
//        }

//        val oldValueList = objectToChange.values

//        // Process indices
//        dataModel.indices?.forEach {
//            val oldValue = it.toStorageByteArrayForIndex(objectToChange, objectToChange.key.bytes)
//            // Use switch trick to use less object creation and still be able to get values
//            objectToChange.values = newValueList
//            val newValue = it.toStorageByteArrayForIndex(objectToChange, objectToChange.key.bytes)
//            objectToChange.values = oldValueList
//
//            if (newValue == null) {
//                if (oldValue != null) {
//                    dataStore.removeFromIndex(objectToChange, it.toReferenceStorageByteArray(), version, oldValue)
//                } // else ignore since did not exist
//            } else if (oldValue == null || !newValue.contentEquals(oldValue)) {
//                dataStore.addToIndex(objectToChange, it.toReferenceStorageByteArray(), newValue, version, oldValue)
//            }
//        }

        // Nothing skipped out so must be a success
        return ChangeSuccess(version.timestamp, outChanges)
    } catch (e: Throwable) {
        return ServerFail(e.toString(), e)
    }
}


/**
 * Create a ValueWriter into [transaction] in [columnFamilies] at [versionBytes]
 * for values at [key]
 */
private fun createValueWriter(
    dataStore: RocksDBDataStore,
    storeAction: StoreAction<*, *, *, *>,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    versionBytes: ByteArray
): ValueWriter<IsPropertyDefinition<*>> = { type, reference, definition, value ->
    when (type) {
        ObjectDelete -> {} // Cannot happen on new add
        Value -> {
            val storableDefinition = Value.castDefinition(definition)
            val valueBytes = storableDefinition.toStorageBytes(value, NO_TYPE_INDICATOR)

            // If a unique index, check if exists, and then write
            if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                val uniqueReference = byteArrayOf(*reference, *valueBytes)

//                checksBeforeWrite.add {
//                    // Since it is an addition we only need to check the current uniques
//                    dataStore.db.get(columnFamilies.unique, uniqueReference)?.let {
//                        throw UniqueException(reference)
//                    }
//                }

                // Creates index reference on table if it not exists so delete can find
                // what values to delete from the unique indices.
                dataStore.createUniqueIndexIfNotExists(storeAction.dbIndex, columnFamilies.unique, reference)
                setUniqueIndexValue(columnFamilies, transaction, uniqueReference, versionBytes, key)
            }

            setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
        }
        ListSize,
        SetSize,
        MapSize -> setValue(transaction, columnFamilies, key, reference, versionBytes, (value as Int).toVarBytes())
        TypeValue -> {
            val typedValue = value as TypedValue<TypeEnum<*>, *>
            val typeDefinition = TypeValue.castDefinition(definition)

            var index = 0
            if (typedValue.value == Unit) {
                val valueBytes = ByteArray(value.type.index.calculateVarIntWithExtraInfoByteSize())
                typedValue.type.index.writeVarIntWithExtraInfo(COMPLEX_TYPE_INDICATOR) { valueBytes[index++] = it }

                setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
            } else {
                val typeValueDefinition = typeDefinition.definition(typedValue.type) as IsSimpleValueDefinition<Any, *>
                val valueBytes = ByteArray(
                    typedValue.type.index.calculateVarIntWithExtraInfoByteSize() +
                        typeValueDefinition.calculateStorageByteLength(typedValue.value)
                )
                val writer: (Byte) -> Unit = { valueBytes[index++] = it }

                typedValue.type.index.writeVarIntWithExtraInfo(SIMPLE_TYPE_INDICATOR, writer)
                typeValueDefinition.writeStorageBytes(typedValue.value, writer)

                setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
            }
        }
        Embed -> {
            // Indicates value exists and is an embed
            // Is for the root of embed
            val valueBytes = byteArrayOf(EMBED_INDICATOR, TRUE)
            setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
        }
    }
}
