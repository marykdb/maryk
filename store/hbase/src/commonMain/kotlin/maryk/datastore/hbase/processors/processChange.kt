package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.processors.datastore.ValueWriter
import maryk.core.processors.datastore.writeIncMapAdditionsToStorage
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.writeTypedValueToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.Multiple
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
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.IsIndexUpdate
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.EmptyValueItems
import maryk.core.values.IsValuesGetter
import maryk.core.values.Values
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.UniqueCheck
import maryk.datastore.hbase.helpers.countValueAsBytes
import maryk.datastore.hbase.helpers.createCellFilterWithPrefix
import maryk.datastore.hbase.helpers.createCountUpdater
import maryk.datastore.hbase.helpers.deleteByReference
import maryk.datastore.hbase.helpers.doesCurrentNotContainExactQualifierAndValue
import maryk.datastore.hbase.helpers.getCurrentIncMapKey
import maryk.datastore.hbase.helpers.getList
import maryk.datastore.hbase.helpers.readValue
import maryk.datastore.hbase.helpers.setListValue
import maryk.datastore.hbase.helpers.setTypedValue
import maryk.datastore.hbase.helpers.toFamilyName
import maryk.datastore.hbase.helpers.unsetNonChangedCells
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.hbase.uniquesColumnFamily
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.RowMutations
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.ColumnPrefixFilter
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.NullComparator
import org.apache.hadoop.hbase.filter.QualifierFilter
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter

internal suspend fun <DM : IsRootDataModel> processChange(
    dataStore: HbaseDataStore,
    dataModel: DM,
    key: Key<DM>,
    lastVersion: ULong?,
    changes: List<IsChange>,
    version: HLC,
    conditionalFilters: MutableList<Filter>,
    rowMutations: RowMutations,
    dependentPuts: MutableList<Put>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsChangeResponseStatus<DM> {
    val table = dataStore.getTable(dataModel)

    val currentRowResult = table.get(Get(key.bytes).apply {
        addFamily(dataColumnFamily)
        val orFilters = mutableListOf<Filter>()

        // Fetch all data needed to reconstruct an index if it is a Multiple
        // Single data indices should already be fetched by the change
        dataModel.Meta.indices?.forEach { indexable ->
            if (indexable is Multiple) {
                indexable.references.forEach {
                    orFilters += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(it.toQualifierStorageByteArray()))
                }
            }
        }

        for (change in changes) {
            // Get all values needed to process the change
            when (change) {
                is Check -> change.referenceValuePairs.forEach {
                    orFilters += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(it.reference.toStorageByteArray()))
                }
                is Change -> change.referenceValuePairs.forEach {
                    val reference = it.reference
                    orFilters += if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null) {
                        ColumnPrefixFilter(reference.parentReference!!.toStorageByteArray())
                    } else {
                        ColumnPrefixFilter(it.reference.toStorageByteArray())
                    }
                }
                is IncMapChange -> change.valueChanges.forEach {
                    val reference = it.reference
                    orFilters += if (reference.parentReference != null) {
                        ColumnPrefixFilter(reference.parentReference!!.toStorageByteArray())
                    } else {
                        ColumnPrefixFilter(it.reference.toStorageByteArray())
                    }
                }
                is ListChange -> change.listValueChanges.forEach {
                    orFilters += ColumnPrefixFilter(it.reference.toStorageByteArray())
                }
                is SetChange -> change.setValueChanges.forEach {
                    orFilters += ColumnPrefixFilter(it.reference.toStorageByteArray())
                }
                is Delete -> change.references.forEach {
                    orFilters += ColumnPrefixFilter(it.toStorageByteArray())
                    orFilters += if (it is IsPropertyReferenceWithParent<*, *, *, *> && (it is ListItemReference<*, *> || it is SetItemReference<*, *> || it is MapValueReference<*, *, *>) && it.parentReference != null) {
                        ColumnPrefixFilter(it.parentReference!!.toStorageByteArray())
                    } else {
                        ColumnPrefixFilter(it.toStorageByteArray())
                    }
                }
            }
        }

        orFilters += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(MetaColumns.CreatedVersion.byteArray))
        orFilters += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(MetaColumns.LatestVersion.byteArray))

        setFilter(FilterList(FilterList.Operator.MUST_PASS_ONE, orFilters))
    }).await()

    val lastVersionBytes = currentRowResult.getValue(dataColumnFamily, MetaColumns.LatestVersion.byteArray)
        ?: return DoesNotExist(key)

    // Exists check in change
    conditionalFilters += SingleColumnValueFilter(dataColumnFamily, MetaColumns.CreatedVersion.byteArray, CompareOperator.NOT_EQUAL, NullComparator())

    // Add expected version check with row which it validated against. Change will fail if newer.
    // This will also guarantee that any checks are done on the correct data
    conditionalFilters += SingleColumnValueFilter(dataColumnFamily, MetaColumns.LatestVersion.byteArray, CompareOperator.EQUAL, BinaryComparator(lastVersionBytes))

    // Check if version is expected last version
    if (lastVersion != null) {
        val lastVersionFromStore = HLC.fromStorageBytes(lastVersionBytes)

        if (lastVersionFromStore.timestamp != lastVersion) {
            return ValidationFail(
                listOf(
                    InvalidValueException(null, "Version of object was different than given: $lastVersion < ${lastVersionFromStore.timestamp}")
                )
            )
        }
    }

    return applyChanges(
        table,
        dataModel,
        currentRowResult,
        rowMutations,
        dependentPuts,
        version,
        key,
        changes,
        updateSharedFlow
    )
}


/**
 * Apply [changes] and record them as [version]
 */
private suspend fun <DM : IsRootDataModel> applyChanges(
    table: AsyncTable<AdvancedScanResultConsumer>,
    dataModel: DM,
    currentRowResult: Result,
    rowMutations: RowMutations,
    dependentPuts: MutableList<Put>,
    version: HLC,
    key: Key<DM>,
    changes: List<IsChange>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsChangeResponseStatus<DM> {
    try {
        val put = Put(key.bytes).setTimestamp(version.timestamp.toLong())
        var validationExceptions: MutableList<ValidationException>? = null
        val uniqueChecksAndChangesBeforeWrite = mutableListOf<UniqueCheck<DM>>()

        fun addValidationFail(ve: ValidationException) {
            if (validationExceptions == null) {
                validationExceptions = mutableListOf()
            }
            validationExceptions!!.add(ve)
        }

        var isChanged = false
        val setChanged = { didChange: Boolean -> if (didChange) isChanged = true }

        val outChanges = mutableListOf<IsChange>()

        for (change in changes) {
            try {
                when (change) {
                    is Check -> {
                        for ((reference, value) in change.referenceValuePairs) {
                            val storedValue = currentRowResult.getColumnLatestCell(dataColumnFamily, reference.toStorageByteArray())?.readValue(reference.comparablePropertyDefinition)

                            if (storedValue != value) {
                                addValidationFail(InvalidValueException(reference, value.toString()))
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

                                    val referenceAsBytes = reference.toStorageByteArray()
                                    val currentValues = currentRowResult.rawCells().filter(createCellFilterWithPrefix(referenceAsBytes))
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    mapDefinition.validateWithRef(
                                        if (hadPrevValue) mapOf() else null,
                                        value as Map<Any, Any>
                                    ) { mapReference }

                                    val qualifiersToKeep = mutableListOf<ByteArray>()
                                    val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add, qualifiersToKeep, doesCurrentNotContainExactQualifierAndValue(currentValues))

                                    writeMapToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        mapDefinition,
                                        value
                                    )

                                    // Delete unneeded old values
                                    unsetNonChangedCells(currentValues, qualifiersToKeep, put)
                                }
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val listDefinition = reference.propertyDefinition as? IsListDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsListDefinition for List change")

                                    val referenceAsBytes = reference.toStorageByteArray()
                                    val currentValues = currentRowResult.rawCells().filter(createCellFilterWithPrefix(referenceAsBytes))
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    listDefinition.validateWithRef(
                                        if (hadPrevValue) listOf() else null,
                                        value as List<Any>
                                    ) { reference as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }

                                    val qualifiersToKeep = mutableListOf<ByteArray>()
                                    val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add, qualifiersToKeep, doesCurrentNotContainExactQualifierAndValue(currentValues))

                                    writeListToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )

                                    // Delete unneeded old values
                                    unsetNonChangedCells(currentValues, qualifiersToKeep, put)
                                }
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val setDefinition = reference.propertyDefinition as? IsSetDefinition<Any, IsPropertyContext>
                                        ?: throw TypeException("Expected a Reference to IsSetDefinition for Set change")

                                    val referenceAsBytes = reference.toStorageByteArray()
                                    val currentValues = currentRowResult.rawCells().filter(createCellFilterWithPrefix(referenceAsBytes))
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    setDefinition.validateWithRef(
                                        if (hadPrevValue) setOf() else null,
                                        value as Set<Any>
                                    ) { reference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }

                                    val qualifiersToKeep = mutableListOf<ByteArray>()
                                    val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add, qualifiersToKeep, doesCurrentNotContainExactQualifierAndValue(currentValues))

                                    writeSetToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )

                                    // Delete unneeded old values
                                    unsetNonChangedCells(currentValues, qualifiersToKeep, put)
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
                                    val hadPrevValue = deleteByReference(currentRowResult, put, reference, reference.toStorageByteArray()) { _, prevTypedValue ->
                                        prevValue = prevTypedValue
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    multiTypeDefinition.validateWithRef(
                                        if (hadPrevValue) prevValue else null,
                                        value as TypedValue<MultiTypeEnum<Any>, Any>
                                    ) { multiTypeReference }

                                    val qualifiersToKeep = mutableListOf<ByteArray>()
                                    val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add, qualifiersToKeep, doesCurrentNotContainExactQualifierAndValue(emptyList()))

                                    writeTypedValueToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter,
                                        reference.propertyDefinition,
                                        value
                                    )
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


                                    val referenceAsBytes = reference.toStorageByteArray()
                                    val currentValues = currentRowResult.rawCells().filter(createCellFilterWithPrefix(referenceAsBytes))
                                    val hadPrevValue = currentValues.isNotEmpty()

                                    @Suppress("UNCHECKED_CAST")
                                    valuesDefinition.validateWithRef(
                                        if (hadPrevValue) valuesDefinition.dataModel.values(null) { EmptyValueItems } else null,
                                        value as Values<IsValuesDataModel>
                                    ) { valuesReference }

                                    val qualifiersToKeep = mutableListOf<ByteArray>()
                                    val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add, qualifiersToKeep, doesCurrentNotContainExactQualifierAndValue(currentValues))

                                    // Write complex values existence indicator
                                    // Write parent value with Unit, so it knows this one is not deleted. So possible lingering old types are not read.
                                    valueWriter(
                                        StorageTypeEnum.Embed,
                                        reference.toStorageByteArray(),
                                        valuesDefinition,
                                        Unit
                                    )

                                    value.writeToStorage(
                                        reference.calculateStorageByteLength(),
                                        reference::writeStorageBytes,
                                        valueWriter
                                    )

                                    // Delete unneeded old values
                                    unsetNonChangedCells(currentValues, qualifiersToKeep, put)
                                }
                                else -> {
                                    try {
                                        val referenceAsBytes = reference.toStorageByteArray()

                                        val previousValue =
                                            currentRowResult.getColumnLatestCell(dataColumnFamily, referenceAsBytes)?.readValue(reference.comparablePropertyDefinition)

                                        if (value == previousValue) {
                                            // Skip if value is the same
                                            continue
                                        }

                                        if (previousValue == null) {
                                            // Check if parent exists before trying to change
                                            if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null) {
                                                currentRowResult.getColumnLatestCell(dataColumnFamily, reference.parentReference!!.toStorageByteArray())?.let {
                                                    if (it.valueArray[it.valueOffset] == TypeIndicator.DeletedIndicator.byte) null else true
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

                                                    createCountUpdater(currentRowResult, reference.parentReference as IsPropertyReference<*, *, *>, put, 1) { count ->
                                                        @Suppress("UNCHECKED_CAST")
                                                        (reference as MapValueReference<Any, Any, IsPropertyContext>).mapDefinition.validateSize(count) {
                                                            reference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *>
                                                        }
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

                                        val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add)

                                        valueWriter(StorageTypeEnum.Value, referenceAsBytes, reference.comparablePropertyDefinition, value)
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
                                //Extra validations based on reference type
                                when (ref) {
                                    is TypedValueReference<*, *, *> -> throw RequestException("Type Reference not allowed for deletes. Use the multi type parent.")
                                    is MapKeyReference<*, *, *> -> throw RequestException("Not allowed to delete Map key, delete value instead")
                                    is MapAnyValueReference<*, *, *> -> throw RequestException("Not allowed to delete Map with any key reference, delete by map reference instead")
                                    is ListAnyItemReference<*, *> -> throw RequestException("Not allowed to delete List with any item reference, delete by list reference instead")
                                    else -> Unit
                                }

                                deleteByReference(currentRowResult, put, reference, referenceAsBytes) { _, previousValue ->
                                    ref.propertyDefinition.validateWithRef(
                                        previousValue = previousValue,
                                        newValue = null,
                                        refGetter = { ref }
                                    )
                                }.also(setChanged)
                            } catch (e: ValidationException) {
                                addValidationFail(e)
                            }
                        }
                    }
                    is ListChange -> {
                        for (listChange in change.listValueChanges) {
                            @Suppress("UNCHECKED_CAST")
                            val originalList = getList(currentRowResult, put, listChange.reference as ListReference<Any, *>)
                            val list = originalList.toMutableList()
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
                                val listReference = listChange.reference as ListReference<Any, *>

                                listReference.propertyDefinition.validate(
                                    previousValue = originalList,
                                    newValue = list,
                                    parentRefFactory = {
                                        @Suppress("UNCHECKED_CAST")
                                        (listChange.reference as? IsPropertyReferenceWithParent<Any, *, *, *>)?.parentReference
                                    }
                                )

                                setListValue(
                                    currentRowResult = currentRowResult,
                                    put = put,
                                    reference = listReference,
                                    newList = list,
                                    originalCount = originalCount,
                                ).also(setChanged)
                            } catch (e: ValidationException) {
                                addValidationFail(e)
                            }
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

                                            val referenceAsBytes = setItemRef.toStorageByteArray()

                                            // Check if previous value exists and raise count change if it does not
                                            currentRowResult.getColumnLatestCell(dataColumnFamily, referenceAsBytes)?.let {
                                                // Check if parent was deleted
                                                if (it.valueArray[it.valueOffset] == TypeIndicator.DeletedIndicator.byte) null else true
                                            } ?: countChange++ // Add 1 because does not exist

                                            @Suppress("UNCHECKED_CAST")
                                            val valueBytes = (setItemRef.propertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

                                            put.addColumn(dataColumnFamily, referenceAsBytes, valueBytes)
                                            setChanged(true)
                                        } catch (e: ValidationException) {
                                            addException(e)
                                        }
                                    }
                                }
                            }

                            createCountUpdater(currentRowResult, setChange.reference, put, countChange) { count ->
                                setDefinition.validateSize(count) { setReference }
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
                                    currentRowResult,
                                    incMapReference
                                )

                                val valueWriter = createValueWriter(dataModel, put, uniqueChecksAndChangesBeforeWrite::add)

                                val addedKeys = writeIncMapAdditionsToStorage(
                                    currentIncMapKey,
                                    valueWriter,
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

                                createCountUpdater(currentRowResult, valueChange.reference, put, addValues.size) { count ->
                                    incMapDefinition.validateSize(count) { incMapReference }
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
            }
        }

        if (uniqueChecksAndChangesBeforeWrite.isNotEmpty()) {
            var foundExistingUnique = false

            // Check if ref and value duplicates are present in uniqueChecksAndChangesBeforeWrite
            if (uniqueChecksAndChangesBeforeWrite.size > 1) {
                uniqueChecksAndChangesBeforeWrite.forEach { check ->
                    if (
                        uniqueChecksAndChangesBeforeWrite.indexOfFirst {
                            it != check && it.reference.contentEquals(check.reference) && it.value.contentEquals(check.value)
                        } != -1
                    ) {
                        addValidationFail(
                            check.exceptionCreator(Key(check.value))
                        )
                    }
                }
            }

            table.getAll(
                uniqueChecksAndChangesBeforeWrite.map {
                    Get(it.reference).addColumn(uniquesColumnFamily, it.value)
                }
            ).await().forEachIndexed { index, result ->
                val value = uniqueChecksAndChangesBeforeWrite[index].value
                if (!result.isEmpty && (result.getColumnLatestCell(uniquesColumnFamily, value)?.valueLength ?: 0) > 1) {
                    foundExistingUnique = true
                    val check = uniqueChecksAndChangesBeforeWrite[index]
                    val foundKey = Key<DM>(result.getValue(uniquesColumnFamily, check.value))
                    if (foundKey != key) {
                        addValidationFail(
                            check.exceptionCreator(foundKey)
                        )
                    }
                }
            }

            if(!foundExistingUnique) {
                // delete previous value from unique if exists
                for (check in uniqueChecksAndChangesBeforeWrite) {
                    val uniquePut = Put(check.reference).setTimestamp(version.timestamp.toLong())
                    val prevValue = currentRowResult.getValue(dataColumnFamily, check.reference)
                    if (prevValue != null) {
                        uniquePut.addColumn(uniquesColumnFamily, prevValue, softDeleteIndicator)
                    }
                    // add new value to unique
                    uniquePut.addColumn(uniquesColumnFamily, check.value, key.bytes)

                    dependentPuts += uniquePut
                }
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
            // Set latest version
            put.addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray, HLC.toStorageBytes(version))
        }

        // Process indices
        dataModel.Meta.indices?.let { indices ->
            val indexUpdates = mutableListOf<IsIndexUpdate>()

            val currentValuesGetter = object : IsValuesGetter {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>) =
                    currentRowResult.getColumnLatestCell(dataColumnFamily, propertyReference.toStorageByteArray())?.readValue(propertyReference.propertyDefinition) as T?
            }
            val newValuesGetter = object : IsValuesGetter {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>) =
                    put.get(dataColumnFamily, propertyReference.toStorageByteArray())?.lastOrNull()?.readValue(propertyReference.propertyDefinition) as T? ?: currentValuesGetter[propertyReference]
            }

            for (index in indices) {
                val family = index.toFamilyName()
                val oldValue = index.toStorageByteArrayForIndex(currentValuesGetter)
                val newValue = index.toStorageByteArrayForIndex(newValuesGetter)

                if (newValue == null) {
                    if (oldValue != null) {
                        dependentPuts += Put(oldValue).setTimestamp(version.timestamp.toLong()).addColumn(family, key.bytes, softDeleteIndicator)
                        indexUpdates.add(IndexDelete(index.referenceStorageByteArray, Bytes(byteArrayOf(*oldValue, *key.bytes))))
                    } // else ignore since did not exist
                } else if (oldValue == null || !newValue.contentEquals(oldValue)) {
                    if (oldValue != null) {
                        dependentPuts += Put(oldValue).setTimestamp(version.timestamp.toLong()).addColumn(family, key.bytes, softDeleteIndicator)
                    }
                    dependentPuts += Put(newValue).setTimestamp(version.timestamp.toLong()).addColumn(family, key.bytes, trueIndicator)
                    indexUpdates.add(IndexUpdate(index.referenceStorageByteArray, Bytes(byteArrayOf(*newValue, *key.bytes)), oldValue?.let { Bytes(byteArrayOf(*oldValue, *key.bytes)) }))
                }
            }

            if(indexUpdates.isNotEmpty()) {
                outChanges += IndexChange(indexUpdates)
            }
        }

        updateSharedFlow.emit(
            Update.Change(dataModel, key, version.timestamp, changes + outChanges)
        )

        if (!put.isEmpty) {
            rowMutations.add(put as Mutation)
        }

        // Nothing skipped out so must be a success
        return ChangeSuccess(version.timestamp, outChanges)
    } catch (e: Throwable) {
        return ServerFail(e.toString(), e)
    }
}

/**
 * Create a ValueWriter into [put]
 */
private fun <DM: IsRootDataModel> createValueWriter(
    dataModel: DM,
    put: Put,
    addUniqueCheckAndChange: (UniqueCheck<DM>) -> Boolean,
    qualifiersToKeep: MutableList<ByteArray>? = null,
    shouldWrite: ((referenceBytes: ByteArray, valueBytes: ByteArray) -> Boolean)? = null,
): ValueWriter<IsPropertyDefinition<*>> = { type, reference, definition, value ->
    when (type) {
        StorageTypeEnum.ObjectDelete -> Unit // Cannot happen on new add
        StorageTypeEnum.Value -> {
            val storableDefinition = StorageTypeEnum.Value.castDefinition(definition)
            val valueBytes = storableDefinition.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

            // If a unique index, check if exists, and then write
            if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                addUniqueCheckAndChange(UniqueCheck(reference, valueBytes) { key ->
                    var index = 0
                    val ref = dataModel.getPropertyReferenceByStorageBytes(
                        reference.size,
                        { reference[index++] }
                    )
                    AlreadyExistsException(ref, key)
                })
            }

            qualifiersToKeep?.add(reference)
            if (shouldWrite?.invoke(reference, valueBytes) != false) {
                put.addColumn(dataColumnFamily, reference, valueBytes)
            }
        }
        StorageTypeEnum.ListSize,
        StorageTypeEnum.SetSize,
        StorageTypeEnum.MapSize -> {
            val valueBytes = countValueAsBytes(value as Int)
            qualifiersToKeep?.add(reference)
            if (shouldWrite?.invoke(reference, valueBytes) != false) {
                put.addColumn(dataColumnFamily, reference, valueBytes)
            }
        }
        StorageTypeEnum.TypeValue -> setTypedValue(value, definition, put, reference, qualifiersToKeep, shouldWrite)
        StorageTypeEnum.Embed -> {
            // Indicates value exists and is an embed
            // Is for the root of embed
            val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, *trueIndicator)
            qualifiersToKeep?.add(reference)
            if (shouldWrite?.invoke(reference, valueBytes) != false) {
                put.addColumn(dataColumnFamily, reference, valueBytes)
            }
        }
    }
}
