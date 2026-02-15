package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.emptyValues
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeIncMapAdditionsToStorage
import maryk.core.processors.datastore.writeListToStorage
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.writeTypedValueToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetReference
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
import maryk.core.values.IsValuesGetter
import maryk.core.values.Values
import maryk.datastore.foundationdb.clusterlog.ClusterLogChange
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.ByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.asByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.checkParentReference
import maryk.datastore.foundationdb.processors.helpers.createValueWriter
import maryk.datastore.foundationdb.processors.helpers.deleteByReference
import maryk.datastore.foundationdb.processors.helpers.getCurrentIncMapKey
import maryk.datastore.foundationdb.processors.helpers.getCurrentValuesForPrefix
import maryk.datastore.foundationdb.processors.helpers.getLastVersion
import maryk.datastore.foundationdb.processors.helpers.getList
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.handleMapAdditionCount
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.setLatestVersion
import maryk.datastore.foundationdb.processors.helpers.setListValue
import maryk.datastore.foundationdb.processors.helpers.setValue
import maryk.datastore.foundationdb.processors.helpers.unsetNonChangedValues
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.helpers.withCountUpdate
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.foundationdb.processors.helpers.writeHistoricTable
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.helpers.convertToValue
import maryk.datastore.shared.readValue
import maryk.datastore.shared.updates.Update
import maryk.lib.bytes.combineToByteArray

private class EarlyStatus(val status: IsChangeResponseStatus<*>) : RuntimeException()

/**
 * Apply [changes] on a single [key] inside a transaction and write them as [version].
 * Focuses on Check and Change operations for simple properties, embedded values, lists, sets, maps and typed values.
 * Incremental list/set/incMap operations are not implemented.
 */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processChange(
    dataModel: DM,
    key: Key<DM>,
    lastVersion: ULong?,
    changes: List<IsChange>,
    version: HLC,
    tableDirs: IsTableDirectories,
): IsChangeResponseStatus<DM> {
    val dataModelId = getDataModelId(dataModel)
    var checkFailed = false
    val result: IsChangeResponseStatus<DM> = try {
        var updateToEmit: Update<DM>? = null

        runTransaction { tr ->
            tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult()
                ?: return@runTransaction DoesNotExist(key)

            // Validate expected last version if provided
            val latest = tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult()
                ?: return@runTransaction DoesNotExist(key)
            val latestHlc = HLC.fromStorageBytes(latest)
            if (lastVersion != null && latestHlc.timestamp != lastVersion) {
                return@runTransaction ValidationFail(
                    listOf(InvalidValueException(null, "Version of object was different than given: $lastVersion < ${latestHlc.timestamp}"))
                )
            }

            val versionBytes = HLC.toStorageBytes(version)

            // Capture initial index values BEFORE any writes
            val storeGetter = object : IsValuesGetter {
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                    propertyReference: IsPropertyReference<T, D, C>
                ): T? {
                    val keyAndRef = combineToByteArray(key.bytes, propertyReference.toStorageByteArray())
                    @Suppress("UNCHECKED_CAST")
                    return tr.getValue(tableDirs, null, keyAndRef, decryptValue = this@processChange::decryptValueIfNeeded) { valueBytes, offset, length ->
                        valueBytes.convertToValue(propertyReference, offset, length) as T?
                    }
                }
            }
            val initialIndexValues: MutableMap<IsIndexable, ByteArray?> = mutableMapOf()
            dataModel.Meta.indexes?.forEach { idx ->
                initialIndexValues[idx] = idx.toStorageByteArrayForIndex(storeGetter, key.bytes)
            }

            // Fast path: only Check operations — handle deterministically and return
            val onlyChecks = changes.all { it is Check }
            if (onlyChecks) {
                val exceptions = mutableListOf<ValidationException>()
                for (change in changes) {
                    val check = change as Check
                    for ((reference, expected) in check.referenceValuePairs) {
                        val keyAndRef = combineToByteArray(key.bytes, reference.toStorageByteArray())
                        val actual = tr.getValue(tableDirs, null, keyAndRef, decryptValue = this@processChange::decryptValueIfNeeded) { bytes, offset, length ->
                            bytes.convertToValue(reference, offset, length)
                        }
                        if (actual != expected) {
                            exceptions += InvalidValueException(reference, expected.toString())
                        }
                    }
                }
                if (exceptions.isNotEmpty()) {
                    throw EarlyStatus(ValidationFail<DM>(exceptions))
                } else {
                    return@runTransaction ChangeSuccess(version.timestamp, emptyList())
                }
            }

            val validationExceptions = mutableListOf<ValidationException>()
            var isChanged = false

            fun addValidation(e: ValidationException) { validationExceptions += e }

            // Keep track of new values for index computation (overlay)
            val overlay = mutableMapOf<ByteArrayKey, Any?>()

            // Helper to put overlay value
            fun putOverlay(reference: IsPropertyReference<*, *, *>, value: Any?) {
                overlay[reference.toStorageByteArray().asByteArrayKey(copy = true)] = value
            }

            // Snapshot getter no longer needed for 'old' — we captured initial values already
            // Overlay getter to see post-change state for index computation
            val overlayGetter = object : IsValuesGetter {
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                    propertyReference: IsPropertyReference<T, D, C>
                ): T? {
                    val refBytes = propertyReference.toStorageByteArray()
                    // First check overlay map
                    overlay[refBytes.asByteArrayKey()]?.let { @Suppress("UNCHECKED_CAST") return it as T? }
                    // Fallback to current store
                    return storeGetter[propertyReference]
                }
            }

            // moved to helpers: getCurrentValuesForPrefix, unsetNonChangedValues

            val outChanges = mutableListOf<IsChange>()
            var earlyStatus: IsChangeResponseStatus<DM>? = null

            try {
                changeLoop@ for (change in changes) {
                    when (change) {
                        is ObjectSoftDeleteChange -> {
                            val softDeleteKey = key.bytes + SOFT_DELETE_INDICATOR
                            val wasDeleted = tr.getValue(tableDirs, null, softDeleteKey, decryptValue = this@processChange::decryptValueIfNeeded) { b, o, _ ->
                                b[o] == TRUE
                            } == true

                            if (wasDeleted == change.isDeleted) {
                                continue@changeLoop
                            }

                            setValue(
                                tr,
                                tableDirs,
                                key.bytes,
                                byteArrayOf(SOFT_DELETE_INDICATOR),
                                versionBytes,
                                byteArrayOf(if (change.isDeleted) TRUE else 0)
                            )
                            isChanged = true
                        }
                        is Check -> {
                            for ((reference, expected) in change.referenceValuePairs) {
                                val refBytes = reference.toStorageByteArray()
                                val packed = tr.get(packKey(tableDirs.tablePrefix, key.bytes, refBytes)).awaitResult()
                                if (packed == null) {
                                    addValidation(InvalidValueException(reference, expected.toString()))
                                } else {
                                    val stored = decryptValueIfNeeded(packed.copyOfRange(VERSION_BYTE_SIZE, packed.size))
                                    try {
                                        val storable = Value.castDefinition(reference.propertyDefinition)
                                        @Suppress("UNCHECKED_CAST")
                                        val expectedBytes = (storable as IsStorageBytesEncodable<Any>).toStorageBytes(expected, TypeIndicator.NoTypeIndicator.byte)
                                        if (!stored.contentEquals(expectedBytes)) {
                                            addValidation(InvalidValueException(reference, expected.toString()))
                                        }
                                    } catch (_: Throwable) {
                                        // Fallback to decoded compare if not a simple value definition
                                        var readIndex = 0
                                        val actual = readValue(reference.comparablePropertyDefinition, { stored[readIndex++] }) { stored.size - readIndex }
                                        if (actual != expected) {
                                            checkFailed = true
                                        }
                                    }
                                }
                            }
                            if (validationExceptions.isNotEmpty()) {
                                earlyStatus = when (validationExceptions.size) {
                                    1 -> ValidationFail(validationExceptions.first())
                                    else -> ValidationFail(validationExceptions)
                                }
                                break@changeLoop
                            }
                        }
                        is Change -> {
                            for (pair in change.referenceValuePairs) {
                                @Suppress("UNCHECKED_CAST")
                                val reference = pair.reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                                val referenceBytes = reference.toStorageByteArray()
                                when (val value = pair.value) {
                                    null -> {
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            val ref = reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
                                            val didChange = deleteByReference(
                                                dataModelId = dataModelId,
                                                tr,
                                                tableDirs,
                                                key,
                                                ref,
                                                referenceBytes,
                                                versionBytes
                                            ) { prevValue ->
                                                try {
                                                    ref.propertyDefinition.validateWithRef(previousValue = prevValue, newValue = null) { ref }
                                                } catch (e: ValidationException) { addValidation(e) }
                                            }
                                            if (didChange) isChanged = true
                                            putOverlay(reference, null)
                                        } catch (e: ValidationException) { addValidation(e) }
                                    }
                                    is Map<*, *> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val mapDef = reference.propertyDefinition as? IsMapDefinition<Any, Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsMapDefinition for Map change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = current.isNotEmpty()
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            mapDef.validateWithRef(if (hadPrev) mapOf() else null, value as Map<Any, Any>) { reference as MapReference<Any, Any, IsPropertyContext> }
                                        } catch (e: ValidationException) { addValidation(e) }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current) { didWrite = true }
                                        checkParentReference(reference)
                                        @Suppress("UNCHECKED_CAST")
                                        writeMapToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, mapDef, value as Map<Any, Any>)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) isChanged = true
                                        putOverlay(reference, value)
                                    }
                                    is List<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val listDef = reference.propertyDefinition as? IsListDefinition<Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsListDefinition for List change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = current.isNotEmpty()
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            listDef.validateWithRef(if (hadPrev) listOf() else null, value as List<Any>) { reference as ListReference<Any, IsPropertyContext> }
                                        } catch (e: ValidationException) { addValidation(e) }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current) { didWrite = true }
                                        checkParentReference(reference)
                                        writeListToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) isChanged = true
                                        putOverlay(reference, value)
                                    }
                                    is Set<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val setDef = reference.propertyDefinition as? IsSetDefinition<Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsSetDefinition for Set change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = current.isNotEmpty()
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            setDef.validateWithRef(if (hadPrev) setOf() else null, value as Set<Any>) { reference as SetReference<Any, IsPropertyContext> }
                                        } catch (e: ValidationException) { addValidation(e) }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current) { didWrite = true }
                                        checkParentReference(reference)
                                        writeSetToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) isChanged = true
                                        putOverlay(reference, value)
                                    }
                                    is TypedValue<*, *> -> {
                                        if (reference.propertyDefinition !is IsMultiTypeDefinition<*, *, *>) {
                                            throw TypeException("Expected a Reference to IsMultiTypeDefinition for TypedValue change")
                                        }
                                        // Delete existing typed value placeholder entries
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        for ((qualifier, _) in current) {
                                            tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
                                            writeHistoricTable(tr, tableDirs, key.bytes, qualifier, versionBytes, EMPTY_BYTEARRAY)
                                        }
                                        val keep = mutableListOf<ByteArray>()
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, null)
                                        checkParentReference(reference)
                                        writeTypedValueToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        isChanged = true
                                        putOverlay(reference, value)
                                    }
                                    is Values<*> -> {
                                        // Support embedded values both as direct property and as map values
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        // Validate only when the property definition exposes an embedded values definition
                                        @Suppress("UNCHECKED_CAST")
                                        (reference.propertyDefinition as? IsEmbeddedValuesDefinition<IsValuesDataModel, IsPropertyContext>)?.let { valuesDef ->
                                            val hadPrev = current.isNotEmpty()
                                            try {
                                                val valuesRef = reference as IsPropertyReference<Values<IsValuesDataModel>, IsPropertyDefinition<Values<IsValuesDataModel>>, *>
                                                valuesDef.validateWithRef(
                                                    if (hadPrev) valuesDef.dataModel.emptyValues() else null,
                                                    value as Values<IsValuesDataModel>
                                                ) { valuesRef }
                                            } catch (e: ValidationException) { addValidation(e) }
                                        }

                                        val keep = mutableListOf<ByteArray>()
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current)
                                        // Marker for embedded object
                                        writer(Embed, referenceBytes, reference.propertyDefinition, Unit)
                                        @Suppress("UNCHECKED_CAST")
                                        (value as Values<IsValuesDataModel>).writeToStorage(
                                            reference.calculateStorageByteLength(),
                                            reference::writeStorageBytes,
                                            writer
                                        )
                                        // If this is a map value insertion and no previous existed, bump the map's count
                                        if (current.isEmpty() && reference is MapValueReference<*, *, *>) {
                                            @Suppress("UNCHECKED_CAST")
                                            val parentRef = reference.parentReference as MapReference<Any, Any, IsPropertyContext>
                                            withCountUpdate(
                                                tr, tableDirs, key, parentRef, +1, versionBytes
                                            ) { newCount ->
                                                parentRef.propertyDefinition.validateSize(newCount) { parentRef }
                                            }
                                        }
                                        unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        isChanged = true
                                        putOverlay(reference, value)
                                    }
                                    else -> {
                                        // Simple value (including list/map/set item refs)
                                        try {
                                            val keyAndReference = combineToByteArray(key.bytes, referenceBytes)
                                            val previousValue = tr.getValue(tableDirs, null, keyAndReference, decryptValue = this@processChange::decryptValueIfNeeded) { b, o, l ->
                                                b.convertToValue(reference, o, l)
                                            }

                                            if (value == previousValue) {
                                                // Skip if value is unchanged
                                                continue
                                            }

                                            if (previousValue == null) {
                                                if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null) {
                                                    val parentExists = tr.getValue(
                                                        tableDirs,
                                                        null,
                                                        combineToByteArray(key.bytes, reference.parentReference!!.toStorageByteArray()),
                                                        decryptValue = this@processChange::decryptValueIfNeeded
                                                    ) { b, o, _ ->
                                                        if (b[o] == TypeIndicator.DeletedIndicator.byte) null else true
                                                    } != null
                                                    if (!parentExists) {
                                                        throw RequestException("Property '${reference.completeName}' can only be changed if parent value exists. Set the parent value with this value.")
                                                    }
                                                }

                                                // Extra validations based on reference type
                                                checkParentReference(reference)
                                                if (reference is MapValueReference<*, *, *>) {
                                                    handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                                }
                                            }

                                            reference.propertyDefinition.validateWithRef(previousValue = previousValue, newValue = value) { reference }

                                            val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes)

                                            // Unique handling if applicable is covered by writer for TypeValue; handle comparable uniques here
                                            val storableDef = try { Value.castDefinition(reference.comparablePropertyDefinition) } catch (_: Throwable) { null }
                                                ?: throw TypeException("Expected a simple value for change on $reference")
                                            @Suppress("UNCHECKED_CAST")
                                            writer(Value, referenceBytes, storableDef as IsPropertyDefinition<*>, value)
                                            isChanged = true
                                            putOverlay(reference, value)
                                        } catch (e: ValidationException) {
                                            addValidation(e)
                                        }
                                    }
                                }
                            }
                        }
                        is ListChange -> {
                            for (listChange in change.listValueChanges) {
                                @Suppress("UNCHECKED_CAST")
                                val originalList = tr.getList(tableDirs, key, listChange.reference as ListReference<Any, *>)
                                val list = originalList.toMutableList()
                                val originalCount = list.size

                                listChange.deleteValues?.forEach { list.remove(it) }
                                listChange.addValuesAtIndex?.forEach { (idx, v) -> list.add(idx.toInt(), v) }
                                listChange.addValuesToEnd?.forEach { list.add(it) }

                                if (list == originalList) {
                                    continue
                                }

                                try {
                                    @Suppress("UNCHECKED_CAST")
                                    val listRef = listChange.reference as ListReference<Any, *>
                                    listRef.propertyDefinition.validate(
                                        previousValue = originalList,
                                        newValue = list,
                                        parentRefFactory = {
                                            @Suppress("UNCHECKED_CAST")
                                            (listChange.reference as? IsPropertyReferenceWithParent<Any, *, *, *>)?.parentReference
                                        }
                                    )

                                    setListValue(tr, tableDirs, key, listRef, list, originalCount, versionBytes).also { if (it) isChanged = true }
                                } catch (e: ValidationException) {
                                    addValidation(e)
                                }
                            }
                        }
                        is SetChange -> {
                            @Suppress("UNCHECKED_CAST")
                            for (setChange in change.setValueChanges) {
                                val setRef = setChange.reference as SetReference<Any, IsPropertyContext>
                                val setDef = setRef.propertyDefinition
                                var countChange = 0

                                setChange.addValues?.let { adds ->
                                    createValidationUmbrellaException({ setRef }) { addException ->
                                        for (value in adds) {
                                            val setItemRef = setDef.itemRef(value, setRef)
                                            try {
                                                setDef.valueDefinition.validateWithRef(null, value) { setItemRef as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>? }

                                                val keyAndRef = combineToByteArray(key.bytes, setItemRef.toStorageByteArray())
                                                val existed = tr.getValue(
                                                    tableDirs,
                                                    null,
                                                    keyAndRef,
                                                    decryptValue = this@processChange::decryptValueIfNeeded
                                                ) { b, o, _ -> if (b[o] == TypeIndicator.DeletedIndicator.byte) null else true } != null
                                                if (!existed) countChange++

                                                @Suppress("UNCHECKED_CAST")
                                                val valueBytes = (setItemRef.propertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                                                setValue(tr, tableDirs, key.bytes, setItemRef.toStorageByteArray(), versionBytes, valueBytes)
                                                isChanged = true
                                            } catch (e: ValidationException) {
                                                addException(e)
                                            }
                                        }
                                    }
                                }

                                if (countChange != 0) {
                                    val countEntry = tr.get(packKey(tableDirs.tablePrefix, key.bytes, setChange.reference.toStorageByteArray())).awaitResult()
                                    val prevCount = countEntry?.let { arr ->
                                        var ri = VERSION_BYTE_SIZE
                                        initIntByVar { arr[ri++] }
                                    } ?: 0
                                    setDef.validateSize((prevCount + countChange).toUInt()) { setRef }
                                    setValue(tr, tableDirs, key.bytes, setChange.reference.toStorageByteArray(), versionBytes, (prevCount + countChange).toVarBytes())
                                }
                            }
                        }
                        is IncMapChange -> {
                            @Suppress("UNCHECKED_CAST")
                            for (valueChange in change.valueChanges) {
                                val incMapRef = valueChange.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>
                                val incDef = incMapRef.propertyDefinition

                                valueChange.addValues?.let { addValues ->
                                    createValidationUmbrellaException({ incMapRef }) { addException ->
                                        for ((i, v) in addValues.withIndex()) {
                                            try {
                                                incDef.valueDefinition.validateWithRef(null, v) { incDef.addIndexRef(i, incMapRef) }
                                            } catch (e: ValidationException) { addException(e) }
                                        }
                                    }

                                    val currentIncKey = getCurrentIncMapKey(tr, tableDirs, key, incMapRef)
                                    val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes)
                                    val addedKeys = writeIncMapAdditionsToStorage(currentIncKey, writer, incMapRef.propertyDefinition.definition, addValues)

                                    if (addedKeys.isNotEmpty()) {
                                        isChanged = true
                                        val addition = outChanges.find { it is IncMapAddition } as IncMapAddition?
                                            ?: IncMapAddition(additions = mutableListOf()).also { outChanges.add(it) }
                                        (addition.additions as MutableList<IncMapKeyAdditions<*, *>>).add(
                                            IncMapKeyAdditions(incMapRef, addedKeys, valueChange.addValues)
                                        )
                                    }

                                    val countEntry = tr.get(packKey(tableDirs.tablePrefix, key.bytes, valueChange.reference.toStorageByteArray())).awaitResult()
                                    val prevCount = countEntry?.let { arr ->
                                        var ri = VERSION_BYTE_SIZE
                                        initIntByVar { arr[ri++] }
                                    } ?: 0
                                    incDef.validateSize((prevCount + addValues.size).toUInt()) { incMapRef }
                                    setValue(tr, tableDirs, key.bytes, valueChange.reference.toStorageByteArray(), versionBytes, (prevCount + addValues.size).toVarBytes())
                                }
                            }
                        }
                        else -> return@runTransaction ServerFail("Unsupported operation $change")
                    }
                }
            } catch (e: ValidationUmbrellaException) {
                validationExceptions += e.exceptions
            } catch (e: ValidationException) {
                addValidation(e)
            } catch (ue: UniqueException) {
                var idx = 0
                val ref = dataModel.getPropertyReferenceByStorageBytes(ue.reference.size, { ue.reference[idx++] })
                throw EarlyStatus(ValidationFail<DM>(listOf(AlreadyExistsException(ref, ue.key))))
            }

            if (earlyStatus != null) {
                throw EarlyStatus(earlyStatus)
            }
            if (validationExceptions.isNotEmpty()) {
                val status = when (validationExceptions.size) {
                    1 -> ValidationFail<DM>(validationExceptions.first())
                    else -> ValidationFail(validationExceptions)
                }
                throw EarlyStatus(status)
            }

            // Update latest version if anything changed
            if (isChanged) {
                val last = getLastVersion(tr, tableDirs, key)
                if (version.timestamp > last) setLatestVersion(tr, tableDirs, key.bytes, versionBytes)
            }

            // Process indexes
            var indexUpdates: MutableList<IsIndexUpdate>? = null
            dataModel.Meta.indexes?.let { indexes ->
                indexUpdates = mutableListOf()
                for (index in indexes) {
                    val oldKeyAndValue = initialIndexValues[index]
                    val newKeyAndValue = index.toStorageByteArrayForIndex(overlayGetter, key.bytes)

                    if (newKeyAndValue == null) {
                        if (oldKeyAndValue != null) {
                            // delete index
                            tr.clear(packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, oldKeyAndValue))
                            writeHistoricIndex(
                                tr, tableDirs, index.referenceStorageByteArray.bytes, oldKeyAndValue, versionBytes, EMPTY_BYTEARRAY
                            )
                            indexUpdates.add(IndexDelete(index.referenceStorageByteArray, Bytes(oldKeyAndValue)))
                        }
                    } else if (oldKeyAndValue == null || !newKeyAndValue.contentEquals(oldKeyAndValue)) {
                        if (oldKeyAndValue != null) {
                            tr.clear(packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, oldKeyAndValue))
                            writeHistoricIndex(
                                tr, tableDirs, index.referenceStorageByteArray.bytes, oldKeyAndValue, versionBytes, EMPTY_BYTEARRAY
                            )
                        }
                        setIndexValue(tr, tableDirs, index.referenceStorageByteArray.bytes, newKeyAndValue, versionBytes)
                        indexUpdates.add(IndexUpdate(index.referenceStorageByteArray, Bytes(newKeyAndValue), oldKeyAndValue?.let { Bytes(it) }))
                    }
                }
            }

            // Prepare change list for response and update stream
            indexUpdates?.let {
                if (it.isNotEmpty()) {
                    outChanges += IndexChange(it)
                }
            }

            val finalChanges = changes + outChanges
            updateToEmit = Update.Change(dataModel, key, version.timestamp, finalChanges)

            clusterUpdateLog?.append(
                tr = tr,
                modelId = dataModelId,
                update = ClusterLogChange(Bytes(key.bytes), version.timestamp, finalChanges),
            )

            ChangeSuccess(version.timestamp, outChanges)
        }.also {
            if (it is ChangeSuccess<DM>) {
                emitUpdate(updateToEmit)
            }
        }
    } catch (e: EarlyStatus) {
        @Suppress("UNCHECKED_CAST")
        e.status as IsChangeResponseStatus<DM>
    } catch (t: Throwable) {
        val cause = t.unwrapFdb()
        if (cause is EarlyStatus) {
            @Suppress("UNCHECKED_CAST")
            cause.status as IsChangeResponseStatus<DM>
        } else {
            ServerFail(cause.toString(), cause)
        }
    }
    return if (checkFailed) {
        ValidationFail(listOf(InvalidValueException(null, "Check failed")))
    } else result
}
