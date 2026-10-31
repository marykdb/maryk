package maryk.datastore.foundationdb.processors

import maryk.core.properties.definitions.validateValueWithRef
import maryk.core.properties.definitions.validateValueType
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
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
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.toListIndex
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
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
import maryk.datastore.foundationdb.HistoricTableDirectories
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
import maryk.datastore.foundationdb.processors.helpers.hasActiveStoredType
import maryk.datastore.foundationdb.processors.helpers.handleMapAdditionCount
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.requireVersionedValue
import maryk.datastore.foundationdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.setLatestVersion
import maryk.datastore.foundationdb.processors.helpers.setListValue
import maryk.datastore.foundationdb.processors.helpers.setValue
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.unsetNonChangedValues
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.shared.diffIndexValues
import maryk.datastore.foundationdb.processors.helpers.withCountUpdate
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.foundationdb.processors.helpers.writeHistoricTable
import maryk.datastore.foundationdb.processors.helpers.writeHistoricUnique
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.areSameStoredScalarValues
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.readValue
import maryk.datastore.shared.readStoredCount
import maryk.datastore.shared.findStoredFinalParent
import maryk.datastore.shared.requireWritableValueReference
import maryk.datastore.shared.requireStoredReferenceParents
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.normalizeReplicationChanges
import maryk.datastore.shared.helpers.convertToValue
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.Update
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.matchesRangePart

private class EarlyStatus(val status: IsChangeResponseStatus<*>) : RuntimeException()

/**
 * Apply [changes] on a single [key] inside a transaction and write them as [version].
 * Focuses on Check and Change operations for simple properties, embedded values, lists, sets, maps and typed values.
 * Supports incremental list, set, and incrementing-map operations.
 */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processChange(
    dataModel: DM,
    key: Key<DM>,
    lastVersion: ULong?,
    changes: List<IsChange>,
    version: HLC,
    tableDirs: IsTableDirectories,
    ignoreIfVersionNotNewer: Boolean = false,
): IsChangeResponseStatus<DM> = processNormalizedChange(
    dataModel, key, lastVersion,
    if (ignoreIfVersionNotNewer) normalizeReplicationChanges(changes) else changes,
    version, tableDirs, ignoreIfVersionNotNewer,
)

private suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processNormalizedChange(
    dataModel: DM,
    key: Key<DM>,
    lastVersion: ULong?,
    changes: List<IsChange>,
    version: HLC,
    tableDirs: IsTableDirectories,
    ignoreIfVersionNotNewer: Boolean = false,
): IsChangeResponseStatus<DM> {
    val dataModelId = getDataModelId(dataModel)
    val result: IsChangeResponseStatus<DM> = try {
        var updateToEmit: Update<DM>? = null

        runRequestTransaction(dataModelId) { tr ->
            updateToEmit = null
            val keyBytes = key.bytes
            fun hasActiveType(reference: AnyPropertyReference) = tr.hasActiveStoredType(tableDirs, keyBytes, reference, null) { modelId, value, offset, length, recordKey, qualifier ->
                decryptValueIfNeeded(modelId, recordKey, qualifier, value, offset, length)
            }
            fun requireActiveType(reference: AnyPropertyReference) {
                if (!hasActiveType(reference)) throw RequestException("Property '${reference.completeName}' belongs to an inactive type. Set the multi type parent first.")
            }
            fun hasStoredValue(reference: AnyPropertyReference) = tr.getValue(tableDirs, null,
                keyBytes + reference.toStorageByteArray(), keyLength = keyBytes.size) { bytes, offset, length ->
                hasStoredValuePayload(reference.comparablePropertyDefinition, bytes, offset, length)
            } == true

            fun requireParents(reference: AnyPropertyReference, allowMissingMapParent: Boolean = false) = requireStoredReferenceParents(reference,
                allowMissingMapParent = allowMissingMapParent,
                hasValue = ::hasStoredValue,
                readCount = { parent -> tr.getValue(tableDirs, null, keyBytes + parent.toStorageByteArray(), keyLength = keyBytes.size) { bytes, offset, length ->
                    readStoredCount(bytes, offset, length)
                } },
            )
            val tombstoneRead = if (ignoreIfVersionNotNewer) {
                tr.get(packKey(tableDirs.replicationTombstonePrefix, keyBytes))
            } else null
            val createdRead = tr.get(packKey(tableDirs.keysPrefix, keyBytes))
            val latestRead = tr.get(packKey(tableDirs.tablePrefix, keyBytes))
            val (createdBytes, latest, tombstoneVersion) = try {
                Triple(
                    createdRead.awaitResult(),
                    latestRead.awaitResult(),
                    tombstoneRead?.awaitResult()?.readHLCTimestampIfExact(),
                )
            } finally {
                createdRead.cancel()
                latestRead.cancel()
                tombstoneRead?.cancel()
            }
            if (createdBytes?.readHLCTimestampIfExact() == null) {
                return@runRequestTransaction if (tombstoneVersion != null && version.timestamp <= tombstoneVersion) {
                    ChangeSuccess(tombstoneVersion, emptyList())
                } else {
                    DoesNotExist(key)
                }
            }

            // Validate expected last version if provided
            if (latest == null) return@runRequestTransaction DoesNotExist(key)
            val latestVersion = latest.readHLCTimestampIfExact() ?: return@runRequestTransaction DoesNotExist(key)
            val lastAppliedVersion = maxOf(latestVersion, tombstoneVersion ?: 0uL)
            if (ignoreIfVersionNotNewer && version.timestamp <= lastAppliedVersion) {
                return@runRequestTransaction ChangeSuccess(lastAppliedVersion, emptyList())
            }
            if (lastVersion != null && latestVersion != lastVersion) {
                return@runRequestTransaction ValidationFail(
                    listOf(InvalidValueException(null, "Version of object was different than given: $lastVersion < $latestVersion"))
                )
            }

            // Recompute inside each retry: another writer may have committed a newer record.
            val writeVersion = if (!ignoreIfVersionNotNewer && version.timestamp <= lastAppliedVersion) {
                HLC(lastAppliedVersion).increment()
            } else version
            val versionBytes = HLC.toStorageBytes(writeVersion)
            val requestedChangedReferences = collectRequestedChangedReferences(changes)
            val currentIsDeleted = tr.getValue(
                tableDirs,
                null,
                keyBytes + SOFT_DELETE_INDICATOR,
                decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
            ) { bytes, offset, length -> length == 1 && bytes[offset] == TRUE } == true
            val targetIsDeleted = changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: currentIsDeleted
            val uniqueReferencesToReconcile = if (changes.any { it is ObjectSoftDeleteChange }) null else
                requestedChangedReferences.map { it.toStorageByteArray() }
            val initialUniqueClaims = (if (currentIsDeleted || targetIsDeleted) emptyList() else captureCurrentUniqueClaims(dataModel, tr, tableDirs, key.bytes, uniqueReferencesToReconcile)).toMutableList()
            val rememberUniqueClaim = { reference: ByteArray, candidates: List<ByteArray> ->
                initialUniqueClaims += CurrentUniqueClaim(reference.copyOf(), candidates.map { it.copyOf() })
            }

            // Capture initial index values BEFORE any writes
            val storeGetter = object : IsValuesGetter {
                private val cache = HashMap<IsPropertyReference<*, *, *>, Any?>(8)

                fun clearCache() {
                    cache.clear()
                }

                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                    propertyReference: IsPropertyReference<T, D, C>
                ): T? {
                    if (cache.containsKey(propertyReference)) {
                        @Suppress("UNCHECKED_CAST")
                        return cache[propertyReference] as T?
                    }

                    val value = when (propertyReference) {
                        is IsMapReference<*, *, *, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            tr.readMapByReference(
                                tableDirs,
                                keyBytes,
                                propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>,
                                { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                            ) as T?
                        }

                        is SetReference<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            tr.readSetByReference(
                                tableDirs.tablePrefix,
                                keyBytes,
                                propertyReference as SetReference<Any, IsPropertyContext>
                            ) as T?
                        }

                        is ListReference<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            tr.getList(
                                tableDirs,
                                key,
                                propertyReference as ListReference<Any, IsPropertyContext>,
                                { modelId, value, offset, length, recordKey, qualifier -> decryptValueIfNeeded(modelId, recordKey, qualifier, value, offset, length) },
                            ) as T?
                        }

                        else -> {
                            @Suppress("UNCHECKED_CAST")
                            tr.getValue(
                                tableDirs,
                                null,
                                keyBytes,
                                propertyReference.toStorageByteArray(),
                                decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                            ) { valueBytes, offset, length ->
                                valueBytes.convertToValue(propertyReference, offset, length) as T?
                            }
                        }
                    }

                    cache[propertyReference] = value
                    return value
                }
            }
            val initialIndexValues: MutableMap<IsIndexable, List<ByteArray>> = mutableMapOf()
            dataModel.Meta.indexes?.forEach { idx ->
                initialIndexValues[idx] = try {
                    idx.toStorageByteArraysForIndex(storeGetter, keyBytes)
                } catch (error: Throwable) {
                    error.rethrowIfFatal()
                    if (!error.isSkippableDataError()) {
                        throw error
                    }
                    collectCurrentIndexEntriesForKey(
                        tr = tr,
                        tableDirs = tableDirs,
                        indexReference = idx.referenceStorageByteArray.bytes,
                        key = keyBytes
                    )
                }
            }
            // Clear old snapshot cache so subsequent reads reflect post-write transaction state.
            storeGetter.clearCache()

            fun checkedValues(check: Check): Values<DM>? =
                if (check.referenceValuePairs.any { it.reference.comparablePropertyDefinition !is IsSimpleValueDefinition<*, *> }) {
                    dataModel.readTransactionIntoValuesWithMetaData(
                        tr, createdBytes.readHLCTimestampIfExact()!!, tableDirs, key, null, null,
                        { _, _, reader -> reader() },
                        decryptValue = { modelId, value, offset, length, recordKey, qualifier ->
                            decryptValueIfNeeded(modelId, recordKey, qualifier, value, offset, length)
                        },
                    )?.values
                } else null

            fun checkedValue(reference: IsPropertyReference<Any, *, *>, completeValues: Values<DM>?): Any? =
                if (!hasActiveType(reference)) null else if (reference.comparablePropertyDefinition is IsSimpleValueDefinition<*, *>) {
                    tr.getValue(tableDirs, null, keyBytes, reference.toStorageByteArray(), decryptValue = { modelId, value, offset, length, recordKey, qualifier ->
                        decryptValueIfNeeded(modelId, recordKey, qualifier, value, offset, length)
                    }) { bytes, offset, length -> bytes.convertToValue(reference, offset, length) }
                } else completeValues?.get(reference)

            // Fast path: only Check operations — handle deterministically and return
            val onlyChecks = changes.all { it is Check }
            if (onlyChecks) {
                val exceptions = mutableListOf<ValidationException>()
                for (change in changes) {
                    val check = change as Check
                    val completeValues = checkedValues(check)
                    for ((reference, expected) in check.referenceValuePairs) {
                        val actual = checkedValue(reference, completeValues)
                        if (actual != expected) {
                            exceptions += InvalidValueException(reference, expected.toString())
                        }
                    }
                }
                if (exceptions.isNotEmpty()) {
                    throw EarlyStatus(ValidationFail<DM>(exceptions))
                } else {
                    if (ignoreIfVersionNotNewer) {
                        setLatestVersion(tr, tableDirs, key.bytes, versionBytes)
                        persistDurableClockWatermark(tr, tableDirs, keyBytes, writeVersion)
                        return@runRequestTransaction ChangeSuccess(writeVersion.timestamp, emptyList())
                    }
                    return@runRequestTransaction ChangeSuccess(latestVersion, emptyList())
                }
            }

            val validationExceptions = mutableListOf<ValidationException>()
            var isChanged = false
            fun trackChange(reference: AnyPropertyReference): (Boolean) -> Unit {
                val parent = findStoredFinalParent(reference, ::hasStoredValue)
                return { didChange ->
                    if (didChange) {
                        if (parent != null) throw AlreadySetException(parent)
                        isChanged = true
                    }
                }
            }


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
                            val wasDeleted = tr.getValue(tableDirs, null, softDeleteKey, decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }) { b, o, l ->
                                if (l == 1) b[o] == TRUE else false
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

                            if (change.isDeleted) {
                                getUniqueIndices(dataModelId, tableDirs.uniquePrefix, tr).forEach { reference ->
                                    deleteCurrentUniqueIndexEntryForKey(tr, tableDirs, reference, key.bytes, versionBytes)
                                }
                            }

                            if (!change.isDeleted) {
                                dataModel.Meta.indexes?.forEach { index ->
                                    val indexReference = index.referenceStorageByteArray.bytes
                                    initialIndexValues[index].orEmpty().forEach { valueAndKey ->
                                        setIndexValue(tr, tableDirs, indexReference, valueAndKey, versionBytes)
                                    }
                                }
                            }

                            isChanged = true
                        }
                        is Check -> {
                            val completeValues = checkedValues(change)
                            for ((reference, expected) in change.referenceValuePairs) {
                                if (checkedValue(reference, completeValues) != expected) {
                                    addValidation(InvalidValueException(reference, expected.toString()))
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
                            pairLoop@ for (pair in change.referenceValuePairs) {
                                @Suppress("UNCHECKED_CAST")
                                val reference = pair.reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                                reference.comparablePropertyDefinition.validateValueType(pair.value) { reference }
                                val setChanged = trackChange(reference)
                                if (pair.value == null && !hasActiveType(reference)) continue
                                if (pair.value != null) requireActiveType(reference)
                                requireWritableValueReference(reference, pair.value, ::hasStoredValue)
                                val referenceBytes = reference.toStorageByteArray()
                                if (pair.value != null) {
                                    val canInitializeMap = pair.value is Map<*, *> || pair.value is List<*> ||
                                        pair.value is Set<*> || pair.value is Values<*> || pair.value is TypedValue<*, *> ||
                                        (ignoreIfVersionNotNewer && reference is MapValueReference<*, *, *> && reference.parentReference is IncMapReference<*, *, *>)
                                    requireParents(reference, canInitializeMap)
                                }
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
                                                    ref.propertyDefinition.validateValueWithRef(previousValue = prevValue, newValue = null) { ref }
                                                } catch (e: ValidationException) { addValidation(e) }
                                            }
                                            if (didChange) setChanged(true)
                                            putOverlay(reference, null)
                                        } catch (e: ValidationException) { addValidation(e) }
                                    }
                                    is Map<*, *> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val mapDef = reference.propertyDefinition as? IsMapDefinition<Any, Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsMapDefinition for Map change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = hasCurrentComplexValue(current, reference)
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            mapDef.validateValueWithRef(if (hadPrev) mapOf<Any, Any>() else null, value as Map<Any, Any>) { reference as IsPropertyReference<Map<Any, Any>, IsPropertyDefinition<Map<Any, Any>>, *> }
                                        } catch (e: ValidationException) {
                                            addValidation(e)
                                            continue@pairLoop
                                        }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim) { didWrite = true }
                                        if (!hasCurrentComplexValue(current, reference)) checkParentReference(reference)
                                        if (!hasCurrentComplexValue(current, reference) && reference is MapValueReference<*, *, *>) {
                                            handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                        }
                                        @Suppress("UNCHECKED_CAST")
                                        writeMapToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, mapDef, value)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) setChanged(true)
                                        putOverlay(reference, value)
                                    }
                                    is List<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val listDef = reference.propertyDefinition as? IsListDefinition<Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsListDefinition for List change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = hasCurrentComplexValue(current, reference)
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            listDef.validateValueWithRef(if (hadPrev) listOf<Any>() else null, value as List<Any>) { reference as IsPropertyReference<List<Any>, IsPropertyDefinition<List<Any>>, *> }
                                        } catch (e: ValidationException) {
                                            addValidation(e)
                                            continue@pairLoop
                                        }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim) { didWrite = true }
                                        if (!hasCurrentComplexValue(current, reference)) checkParentReference(reference)
                                        if (!hasCurrentComplexValue(current, reference) && reference is MapValueReference<*, *, *>) {
                                            handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                        }
                                        writeListToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) setChanged(true)
                                        putOverlay(reference, value)
                                    }
                                    is Set<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val setDef = reference.propertyDefinition as? IsSetDefinition<Any, IsPropertyContext>
                                            ?: throw TypeException("Expected a Reference to IsSetDefinition for Set change")
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        val hadPrev = hasCurrentComplexValue(current, reference)
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            setDef.validateValueWithRef(if (hadPrev) setOf<Any>() else null, value as Set<Any>) { reference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *> }
                                        } catch (e: ValidationException) {
                                            addValidation(e)
                                            continue@pairLoop
                                        }
                                        val keep = mutableListOf<ByteArray>()
                                        var didWrite = false
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim) { didWrite = true }
                                        if (!hasCurrentComplexValue(current, reference)) checkParentReference(reference)
                                        if (!hasCurrentComplexValue(current, reference) && reference is MapValueReference<*, *, *>) {
                                            handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                        }
                                        writeSetToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        val didDelete = unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        if (didWrite || didDelete) setChanged(true)
                                        putOverlay(reference, value)
                                    }
                                    is TypedValue<*, *> -> {
                                        if (reference.propertyDefinition !is IsMultiTypeDefinition<*, *, *>) {
                                            throw TypeException("Expected a Reference to IsMultiTypeDefinition for TypedValue change")
                                        }
                                        @Suppress("UNCHECKED_CAST")
                                        val multiDefinition = reference.propertyDefinition as IsMultiTypeDefinition<MultiTypeEnum<Any>, Any, IsPropertyContext>
                                        val previous = tr.getValue(tableDirs, null, keyBytes, referenceBytes,
                                            decryptValue = { modelId, bytes, offset, length, recordKey, qualifier ->
                                                decryptValueIfNeeded(modelId, recordKey, qualifier, bytes, offset, length)
                                            },
                                        ) { bytes, offset, length ->
                                            var readIndex = offset
                                            // Complex values need their discriminator, not a read of every child.
                                            @Suppress("UNCHECKED_CAST")
                                            when (val stored = readValue(multiDefinition, { bytes[readIndex++] }) { offset + length - readIndex }) {
                                                is TypedValue<*, *> -> stored as TypedValue<MultiTypeEnum<Any>, Any>
                                                is MultiTypeEnum<*> -> (stored as MultiTypeEnum<Any>)(Unit)
                                                else -> null
                                            }
                                        }
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            multiDefinition.validateValueWithRef(previous, value as TypedValue<MultiTypeEnum<Any>, Any>) {
                                                reference as IsPropertyReference<TypedValue<MultiTypeEnum<Any>, Any>, IsPropertyDefinition<TypedValue<MultiTypeEnum<Any>, Any>>, *>
                                            }
                                        } catch (e: ValidationException) {
                                            addValidation(e)
                                            continue@pairLoop
                                        }
                                        // Delete existing typed value placeholder entries
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        for ((qualifier, _) in current) {
                                            tr.clear(packKey(tableDirs.tablePrefix, key.bytes, qualifier))
                                            writeHistoricTable(tr, tableDirs, key.bytes, qualifier, versionBytes, HISTORIC_DELETE_MARKER)
                                        }
                                        val keep = mutableListOf<ByteArray>()
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, null, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim)
                                        if (!hasCurrentComplexValue(current, reference)) checkParentReference(reference)
                                        if (!hasCurrentComplexValue(current, reference) && reference is MapValueReference<*, *, *>) {
                                            handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                        }
                                        writeTypedValueToStorage(reference.calculateStorageByteLength(), reference::writeStorageBytes, writer, reference.propertyDefinition, value)
                                        setChanged(true)
                                        putOverlay(reference, value)
                                    }
                                    is Values<*> -> {
                                        // Support embedded values both as direct property and as map values
                                        val current = getCurrentValuesForPrefix(tr, tableDirs, key, referenceBytes)
                                        // Validate only when the property definition exposes an embedded values definition
                                        @Suppress("UNCHECKED_CAST")
                                        val valuesDef = reference.propertyDefinition as? IsEmbeddedValuesDefinition<IsValuesDataModel, IsPropertyContext>
                                        if (valuesDef != null) {
                                            val hadPrev = hasCurrentComplexValue(current, reference)
                                            @Suppress("UNCHECKED_CAST")
                                            try {
                                                val valuesRef = reference as IsPropertyReference<Values<IsValuesDataModel>, IsPropertyDefinition<Values<IsValuesDataModel>>, *>
                                                valuesDef.validateValueWithRef(
                                                    if (hadPrev) valuesDef.dataModel.emptyValues() else null,
                                                    value as Values<IsValuesDataModel>
                                                ) { valuesRef }
                                            } catch (e: ValidationException) {
                                                addValidation(e)
                                                continue@pairLoop
                                            }
                                        }

                                        val keep = mutableListOf<ByteArray>()
                                        val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, keep, current, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim)
                                        // Marker for embedded object
                                        writer(Embed, referenceBytes, reference.propertyDefinition, Unit)
                                        @Suppress("UNCHECKED_CAST")
                                        (value as Values<IsValuesDataModel>).writeToStorage(
                                            reference.calculateStorageByteLength(),
                                            reference::writeStorageBytes,
                                            writer
                                        )
                                        if (!hasCurrentComplexValue(current, reference) && reference is MapValueReference<*, *, *>) {
                                            handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes)
                                        }
                                        unsetNonChangedValues(tr, tableDirs, key, current, keep, versionBytes)
                                        setChanged(true)
                                        putOverlay(reference, value)
                                    }
                                    else -> {
                                        // Simple value (including list/map/set item refs)
                                        try {
                                            val previousValue = tr.getValue(tableDirs, null, keyBytes, referenceBytes, decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }) { b, o, l ->
                                                b.convertToValue(reference, o, l)
                                            }

                                            if (areSameStoredScalarValues(reference.comparablePropertyDefinition, value, previousValue)) {
                                                // Skip if value is unchanged
                                                continue
                                            }

                                            if (previousValue == null) {
                                                if (reference is IsPropertyReferenceWithParent<*, *, *, *> && reference !is ListItemReference<*, *> && reference.parentReference != null &&
                                                    !(ignoreIfVersionNotNewer && reference is MapValueReference<*, *, *> && reference.parentReference is IncMapReference<*, *, *>)) {
                                                    val parentExists = tr.getValue(
                                                        tableDirs,
                                                        null,
                                                        keyBytes,
                                                        reference.parentReference!!.toStorageByteArray(),
                                                        decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                                                    ) { b, o, l ->
                                                        if (hasStoredValuePayload(reference.parentReference!!.comparablePropertyDefinition, b, o, l)) true else null
                                                    } != null
                                                    if (!parentExists) {
                                                        throw RequestException("Property '${reference.completeName}' can only be changed if parent value exists. Set the parent value with this value.")
                                                    }
                                                }

                                                // Extra validations based on reference type
                                                checkParentReference(reference)
                                                if (reference is MapValueReference<*, *, *>) {
                                                    handleMapAdditionCount(tr, tableDirs, key, reference, versionBytes,
                                                        allowMissingParent = ignoreIfVersionNotNewer && reference.parentReference is IncMapReference<*, *, *>)
                                                }
                                            }

                                            reference.propertyDefinition.validateValueWithRef(previousValue = previousValue, newValue = value) { reference }

                                            val writer = createValueWriter(
                                                dataModelId,
                                                tr,
                                                tableDirs,
                                                key,
                                                versionBytes,
                                                skipCurrentUniqueOwnership = targetIsDeleted,
                                                onUniqueClaim = rememberUniqueClaim,
                                            )

                                            // Unique handling if applicable is covered by writer for TypeValue; handle comparable uniques here
                                            val storableDef = try {
                                                Value.castDefinition(reference.comparablePropertyDefinition)
                                            } catch (error: Throwable) {
                                                error.rethrowIfFatal()
                                                null
                                            }
                                                ?: throw TypeException("Expected a simple value for change on $reference")
                                            @Suppress("UNCHECKED_CAST")
                                            writer(Value, referenceBytes, storableDef as IsPropertyDefinition<*>, value)
                                            setChanged(true)
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
                                val setChanged = trackChange(listChange.reference)
                                requireActiveType(listChange.reference)
                                requireParents(listChange.reference)
                                @Suppress("UNCHECKED_CAST")
                                val originalList = tr.getList(tableDirs, key, listChange.reference as TypedPropertyReference<List<Any>>,
                                    { modelId, value, offset, length, recordKey, qualifier -> decryptValueIfNeeded(modelId, recordKey, qualifier, value, offset, length) })
                                val list = originalList.toMutableList()
                                val originalCount = list.size

                                listChange.deleteValues?.forEach { list.remove(it) }
                                listChange.addValuesAtIndex?.forEach { (idx, v) -> list.add(idx.toListIndex(), v) }
                                listChange.addValuesToEnd?.forEach { list.add(it) }

                                if (list == originalList) {
                                    continue
                                }

                                try {
                                    @Suppress("UNCHECKED_CAST")
                                    val listRef = listChange.reference as TypedPropertyReference<List<Any>>
                                    @Suppress("UNCHECKED_CAST")
                                    val listDefinition = listRef.comparablePropertyDefinition as IsListDefinition<Any, IsPropertyContext>
                                    // getList returns an empty list for both absent and explicitly empty values.
                                    val hadList = tr.getValue(tableDirs, null,
                                        keyBytes + listRef.toStorageByteArray(), keyLength = keyBytes.size) { bytes, offset, length ->
                                        readStoredCount(bytes, offset, length)
                                    } != null
                                    val previousList = originalList.takeIf { hadList }
                                    listDefinition.validateValueWithRef(previousList, list) { listRef }

                                    if (!hadList && listRef is MapValueReference<*, *, *>) {
                                        handleMapAdditionCount(tr, tableDirs, key, listRef, versionBytes)
                                    }
                                    setListValue(tr, tableDirs, key, listRef, list, originalCount, versionBytes).also { if (it) setChanged(true) }
                                } catch (e: ValidationException) {
                                    addValidation(e)
                                }
                            }
                        }
                        is SetChange -> {
                            @Suppress("UNCHECKED_CAST")
                            for (setChange in change.setValueChanges) {
                                val setChanged = trackChange(setChange.reference)
                                requireActiveType(setChange.reference)
                                requireParents(setChange.reference)
                                val setRef = setChange.reference as TypedPropertyReference<Set<Any>>
                                val setDef = setRef.comparablePropertyDefinition as IsSetDefinition<Any, IsPropertyContext>
                                val hadSet = tr.getValue(tableDirs, null,
                                    keyBytes + setRef.toStorageByteArray(), keyLength = keyBytes.size) { bytes, offset, length ->
                                    readStoredCount(bytes, offset, length)
                                } != null
                                val finalSetPresent = setDef.final && hadSet
                                var countChange = 0

                                setChange.addValues?.let { adds ->
                                    createValidationUmbrellaException({ setRef }) { addException ->
                                        for (value in adds) {
                                            val setItemRef = setDef.itemRef(value, setRef as CanContainSetItemReference<*, *, *>)
                                            try {
                                                setDef.valueDefinition.validateValueWithRef(null, value) { setItemRef as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>? }

                                                val existed = tr.getValue(
                                                    tableDirs,
                                                    null,
                                                    keyBytes,
                                                    setItemRef.toStorageByteArray(),
                                                    decryptValue = { modelId, value, offset, length, recordKey, reference -> decryptValueIfNeeded(modelId, recordKey, reference, value, offset, length) }
                                                ) { b, o, l -> if (l == 0 || b[o] == TypeIndicator.DeletedIndicator.byte) null else true } != null
                                                if (existed) continue
                                                if (finalSetPresent) throw AlreadySetException(setRef)
                                                countChange++

                                                @Suppress("UNCHECKED_CAST")
                                                val valueBytes = (setItemRef.propertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                                                setValue(tr, tableDirs, key.bytes, setItemRef.toStorageByteArray(), versionBytes, valueBytes)
                                                setChanged(true)
                                            } catch (e: ValidationException) {
                                                addException(e)
                                            }
                                        }
                                    }
                                }

                                if (countChange != 0) {
                                    if (!hadSet && setRef is MapValueReference<*, *, *>) {
                                        handleMapAdditionCount(tr, tableDirs, key, setRef, versionBytes)
                                    }
                                    val countEntry = tr.get(packKey(tableDirs.tablePrefix, key.bytes, setChange.reference.toStorageByteArray())).awaitResult()
                                    val prevCount = countEntry?.let { arr ->
                                        readStoredCount(arr, VERSION_BYTE_SIZE, arr.size - VERSION_BYTE_SIZE)
                                    } ?: 0
                                    setDef.validateSize((prevCount + countChange).toUInt()) { setRef }
                                    setValue(tr, tableDirs, key.bytes, setChange.reference.toStorageByteArray(), versionBytes, (prevCount + countChange).toVarBytes())
                                }
                            }
                        }
                        is IncMapChange -> {
                            @Suppress("UNCHECKED_CAST")
                            for (valueChange in change.valueChanges) {
                                val setChanged = trackChange(valueChange.reference)
                                requireActiveType(valueChange.reference)
                                requireParents(valueChange.reference)
                                val incMapRef = valueChange.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>
                                val incDef = incMapRef.propertyDefinition

                                valueChange.addValues?.let { addValues ->
                                    if (addValues.isEmpty()) return@let
                                    if (incDef.final && tr.getValue(tableDirs, null,
                                            keyBytes + incMapRef.toStorageByteArray(), keyLength = keyBytes.size) { bytes, offset, length ->
                                            readStoredCount(bytes, offset, length)
                                        } != null) {
                                        throw AlreadySetException(incMapRef)
                                    }
                                    createValidationUmbrellaException({ incMapRef }) { addException ->
                                        for ((i, v) in addValues.withIndex()) {
                                            try {
                                                incDef.valueDefinition.validateValueWithRef(null, v) { incDef.addIndexRef(i, incMapRef) }
                                            } catch (e: ValidationException) { addException(e) }
                                        }
                                    }

                                    val currentIncKey = getCurrentIncMapKey(tr, tableDirs, key, incMapRef)
                                    val writer = createValueWriter(dataModelId, tr, tableDirs, key, versionBytes, skipCurrentUniqueOwnership = targetIsDeleted, onUniqueClaim = rememberUniqueClaim)
                                    val addedKeys = writeIncMapAdditionsToStorage(currentIncKey, writer, incMapRef.propertyDefinition.definition, addValues)

                                    if (addedKeys.isNotEmpty()) {
                                        setChanged(true)
                                        val addition = outChanges.find { it is IncMapAddition } as IncMapAddition?
                                            ?: IncMapAddition(additions = mutableListOf()).also { outChanges.add(it) }
                                        (addition.additions as MutableList<IncMapKeyAdditions<*, *>>).add(
                                            IncMapKeyAdditions(incMapRef, addedKeys, valueChange.addValues)
                                        )
                                    }

                                    val countEntry = tr.get(packKey(tableDirs.tablePrefix, key.bytes, valueChange.reference.toStorageByteArray())).awaitResult()
                                    val prevCount = countEntry?.let { arr ->
                                        readStoredCount(arr, VERSION_BYTE_SIZE, arr.size - VERSION_BYTE_SIZE)
                                    } ?: 0
                                    incDef.validateSize((prevCount + addValues.size).toUInt()) { incMapRef }
                                    setValue(tr, tableDirs, key.bytes, valueChange.reference.toStorageByteArray(), versionBytes, (prevCount + addValues.size).toVarBytes())
                                }
                            }
                        }
                        else -> throw EarlyStatus(ServerFail<DM>("Unsupported operation $change"))
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

            if (!isChanged) {
                if (ignoreIfVersionNotNewer) {
                    // An accepted replication event fences older events even when its value is equal.
                    setLatestVersion(tr, tableDirs, key.bytes, versionBytes)
                    persistDurableClockWatermark(tr, tableDirs, keyBytes, writeVersion)
                    return@runRequestTransaction ChangeSuccess(writeVersion.timestamp, outChanges)
                }
                return@runRequestTransaction ChangeSuccess(latestVersion, outChanges)
            }

            reconcileCurrentUniqueClaims(dataModel, tr, tableDirs, key.bytes, versionBytes,
                initialUniqueClaims, targetIsDeleted, uniqueReferencesToReconcile)

            // Update latest version if anything changed
            if (writeVersion.timestamp > latestVersion) setLatestVersion(tr, tableDirs, key.bytes, versionBytes)

            // Process indexes
            var indexUpdates: MutableList<IsIndexUpdate>? = null
            dataModel.Meta.indexes?.let { indexes ->
                indexUpdates = mutableListOf()
                for (index in indexes) {
                    val oldValues = initialIndexValues[index].orEmpty()
                    val finalValues = try {
                        index.toStorageByteArraysForIndex(overlayGetter, key.bytes)
                    } catch (error: Throwable) {
                        error.rethrowIfFatal()
                        if (
                            !error.isSkippableDataError() ||
                            index.isAffectedByAnyReference(requestedChangedReferences)
                        ) {
                            throw error
                        }
                        oldValues
                    }

                    if (targetIsDeleted) {
                        val removedHistoricValues = diffIndexValues(oldValues, finalValues).removed
                        oldValues.forEach { oldValue ->
                            tr.clear(packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, oldValue))
                            indexUpdates.add(IndexDelete(index.referenceStorageByteArray, Bytes(oldValue)))
                        }
                        removedHistoricValues.forEach { oldValue ->
                            writeHistoricIndex(
                                tr,
                                tableDirs,
                                index.referenceStorageByteArray.bytes,
                                oldValue,
                                versionBytes,
                                HISTORIC_REMOVAL_MARKER,
                            )
                        }
                        finalValues.forEach { finalValue ->
                            writeHistoricIndex(
                                tr,
                                tableDirs,
                                index.referenceStorageByteArray.bytes,
                                finalValue,
                                versionBytes,
                                EMPTY_BYTEARRAY,
                            )
                        }
                        continue
                    }

                    val (removed, added) = diffIndexValues(oldValues, finalValues)

                    if (removed.size == 1 && added.size == 1) {
                        val oldValue = removed.first()
                        val newValue = added.first()
                        tr.clear(packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, oldValue))
                        writeHistoricIndex(
                            tr, tableDirs, index.referenceStorageByteArray.bytes, oldValue, versionBytes, HISTORIC_REMOVAL_MARKER
                        )
                        setIndexValue(tr, tableDirs, index.referenceStorageByteArray.bytes, newValue, versionBytes)
                        indexUpdates.add(IndexUpdate(index.referenceStorageByteArray, Bytes(newValue), Bytes(oldValue)))
                    } else {
                        removed.forEach { oldValue ->
                            tr.clear(packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, oldValue))
                            writeHistoricIndex(
                                tr, tableDirs, index.referenceStorageByteArray.bytes, oldValue, versionBytes, HISTORIC_REMOVAL_MARKER
                            )
                            indexUpdates.add(IndexDelete(index.referenceStorageByteArray, Bytes(oldValue)))
                        }

                        added.forEach { newValue ->
                            setIndexValue(tr, tableDirs, index.referenceStorageByteArray.bytes, newValue, versionBytes)
                            indexUpdates.add(IndexUpdate(index.referenceStorageByteArray, Bytes(newValue), null))
                        }
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
            tableDirs.updateHistoryPrefix?.let { prefix ->
                tr.set(packKey(prefix, writeVersion.timestamp.toReversedVersionBytes(), key.bytes), EMPTY_BYTEARRAY)
            }
            persistDurableClockWatermark(tr, tableDirs, keyBytes, writeVersion)
            updateToEmit = Update.Change(dataModel, key, writeVersion.timestamp, finalChanges)

            clusterUpdateLog?.append(
                tr = tr,
                modelId = dataModelId,
                update = ClusterLogChange(Bytes(key.bytes), writeVersion.timestamp, finalChanges),
            )

            ChangeSuccess(writeVersion.timestamp, outChanges)
        }.also {
            if (it is ChangeSuccess<DM>) {
                emitUpdate(updateToEmit)
            }
        }
    } catch (e: EarlyStatus) {
        @Suppress("UNCHECKED_CAST")
        e.status as IsChangeResponseStatus<DM>
    } catch (exception: UniqueException) {
        var offset = 0
        val reference = dataModel.getPropertyReferenceByStorageBytes(exception.reference.size, { exception.reference[offset++] })
        ValidationFail(listOf(AlreadyExistsException(reference, exception.key)))
    } catch (t: Throwable) {
        t.rethrowIfFatal()
        val cause = t.unwrapFdb()
        if (cause is EarlyStatus) {
            @Suppress("UNCHECKED_CAST")
            cause.status as IsChangeResponseStatus<DM>
        } else {
            ServerFail(cause.toString(), cause)
        }
    }
    return result
}

private fun deleteCurrentUniqueIndexEntryForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    reference: ByteArray,
    key: ByteArray,
    versionBytes: ByteArray
) {
    val prefix = packKey(tableDirs.uniquePrefix, reference)
    val iterator = tr.getRange(Range.startsWith(prefix)).iterator()

    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        if (
            kv.value.size == VERSION_BYTE_SIZE + key.size &&
            kv.value.matchesRangePart(VERSION_BYTE_SIZE, key)
        ) {
            tr.clear(kv.key)
            val uniqueRef = kv.key.copyOfRange(tableDirs.uniquePrefix.size, kv.key.size)
            writeHistoricUnique(tr, tableDirs, key, uniqueRef, versionBytes)
        }
    }
}

private fun collectCurrentIndexEntriesForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    key: ByteArray
): List<ByteArray> {
    val prefix = packKey(tableDirs.indexPrefix, indexReference)
    val iterator = tr.getRange(Range.startsWith(prefix)).iterator()
    val valueAndKeys = mutableListOf<ByteArray>()

    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        val indexKeyBytes = kv.key
        val keyOffset = indexKeyBytes.size - key.size
        if (indexKeyBytes.size < prefix.size + key.size) {
            continue
        }
        if (indexKeyBytes.matchesRangePart(keyOffset, key, length = key.size)) {
            valueAndKeys += indexKeyBytes.copyOfRange(prefix.size, indexKeyBytes.size)
        }
    }

    return valueAndKeys
}

private fun collectRequestedChangedReferences(changes: List<IsChange>) = buildList {
    changes.forEach { change ->
        when (change) {
            is Change -> change.referenceValuePairs.forEach {
                val reference = it.reference
                add(reference)
                // Deleting one item shifts every following item's descendants and claims.
                if (it.value == null && reference is ListItemReference<*, *>) add(requireNotNull(reference.parentReference))
            }
            is ListChange -> change.listValueChanges.forEach { add(it.reference) }
            is SetChange -> change.setValueChanges.forEach { add(it.reference) }
            is IncMapChange -> change.valueChanges.forEach { add(it.reference) }
            else -> {}
        }
    }
}

private fun IsIndexable.isAffectedByAnyReference(references: List<AnyPropertyReference>) =
    references.any { reference -> this.isAffectedByReference(reference) }

private fun IsIndexable.isAffectedByReference(reference: AnyPropertyReference): Boolean = when (this) {
    is IsIndexablePropertyReference<*> -> this.isForPropertyReference(reference)
    is Multiple -> this.references.any { it.isAffectedByReference(reference) }
    is AnyOf -> this.references.any { it.isForPropertyReference(reference) }
    else -> false
}

private fun hasCurrentComplexValue(
    currentValues: List<Pair<ByteArray, ByteArray>>,
    reference: IsPropertyReference<*, *, *>,
): Boolean {
    val qualifier = reference.toStorageByteArray()
    val stored = currentValues.firstOrNull { it.first.contentEquals(qualifier) }?.second ?: return false
    return hasStoredValuePayload(reference.comparablePropertyDefinition, stored, VERSION_BYTE_SIZE, stored.size - VERSION_BYTE_SIZE)
}
