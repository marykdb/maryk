package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.currentCoroutineContext
import maryk.foundationdb.FdbFuture
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.foundationdb.FoundationDBBulkAddContext
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.setCreatedVersion
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.setTypedValue
import maryk.datastore.foundationdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.foundationdb.processors.helpers.setValue
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.Update
import maryk.datastore.foundationdb.clusterlog.ClusterLogAddition
import maryk.core.properties.types.Bytes

internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processAdd(
    tableDirs: IsTableDirectories,
    dataModel: DM,
    key: Key<DM>,
    version: HLC,
    objectToAdd: Values<DM>,
    isDeleted: Boolean = false,
    ignoreIfVersionNotNewer: Boolean = false,
): IsAddResponseStatus<DM> = try {
    val keyBytes = key.bytes
    val dataModelId = getDataModelId(dataModel)
    objectToAdd.validate()

    var updateToEmit: Update<DM>? = null
    val initialState = currentCoroutineContext()[FoundationDBBulkAddContext]
        ?.takeIf { it.owner === this }
        ?.initialRecords?.remove(dataModelId to Bytes(keyBytes))

    runRequestTransaction(dataModelId) { tr ->
        updateToEmit = null
        val packedKey = packKey(tableDirs.keysPrefix, keyBytes)
        val tombstoneKey = packKey(tableDirs.replicationTombstonePrefix, keyBytes)

        val existingRead = if (initialState == null) tr.get(packedKey) else null
        var tombstoneRead: FdbFuture<ByteArray?>? = null
        val existing: ByteArray?
        val tombstoneVersion: ULong?
        try {
            tombstoneRead = if (initialState == null) tr.get(tombstoneKey) else null
            existing = if (initialState != null) initialState.existing else requireNotNull(existingRead).await()
            tombstoneVersion = if (initialState != null) initialState.tombstoneVersion
                else requireNotNull(tombstoneRead).await()?.readHLCTimestampIfExact()
        } finally {
            existingRead?.cancel()
            tombstoneRead?.cancel()
        }
        if (ignoreIfVersionNotNewer) {
            val currentVersion = if (existing != null) {
                tr.get(packKey(tableDirs.tablePrefix, keyBytes)).await()?.readHLCTimestampIfExact()
            } else null
            val lastAppliedVersion = listOfNotNull(currentVersion, tombstoneVersion).maxOrNull()
            if (lastAppliedVersion != null && version.timestamp <= lastAppliedVersion) {
                return@runRequestTransaction AddSuccess(key, version.timestamp, emptyList())
            }
        }
        if (existing != null) {
            AlreadyExists(key)
        } else {
            val writeVersion = if (!ignoreIfVersionNotNewer && tombstoneVersion != null && version.timestamp <= tombstoneVersion) {
                HLC(tombstoneVersion).increment()
            } else version
            val versionBytes = HLC.toStorageBytes(writeVersion)
            tr.clear(tombstoneKey)

            // Store first and last version markers
            setCreatedVersion(tr, tableDirs, keyBytes, versionBytes)
            tableDirs.updateHistoryPrefix?.let { prefix ->
                tr.set(packKey(prefix, writeVersion.timestamp.toReversedVersionBytes(), keyBytes), EMPTY_BYTEARRAY)
            }

            var checks: MutableList<() -> Unit>? = null
            var uniqueWrites: MutableList<ByteArray>? = null

            // Indexes
            if (!isDeleted) {
                dataModel.Meta.indexes?.forEach { indexDef ->
                    val indexRef = indexDef.referenceStorageByteArray.bytes
                    indexDef.toStorageByteArraysForIndex(objectToAdd, keyBytes.copyOf()).forEach { valueAndKeyBytes ->
                        setIndexValue(tr, tableDirs, indexRef, valueAndKeyBytes, versionBytes)
                    }
                }
            }

            // Values
            objectToAdd.writeToStorage { type, reference, definition, value ->
                when (type) {
                    ObjectDelete -> Unit
                    Value -> {
                        val storableDef = Value.castDefinition(definition)
                        val valueBytes =
                            storableDef.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

                        // Unique handling
                        if (!isDeleted && (definition is IsComparableDefinition<*, *>) && definition.unique) {
                            val uniqueRefs = mapUniqueValueByteCandidates(dataModelId, reference, valueBytes)
                                .map { uniqueValue -> reference + uniqueValue }
                            val uniqueRef = uniqueRefs.first()
                            createUniqueIndexIfNotExists(dataModelId, tableDirs.uniquePrefix, reference, tr)
                            val pendingChecks = checks ?: ArrayList<() -> Unit>().also { checks = it }
                            pendingChecks += {
                                for (candidateRef in uniqueRefs) {
                                    val uniqueKey = packKey(tableDirs.uniquePrefix, candidateRef)
                                    val uniqueExists = tr.get(uniqueKey).awaitResult()
                                    if (uniqueExists?.size == VERSION_BYTE_SIZE + keyBytes.size) {
                                        val existingKeyBytes = uniqueExists.copyOfRange(
                                            VERSION_BYTE_SIZE,
                                            uniqueExists.size
                                        )
                                        throw UniqueException(reference, Key<DM>(existingKeyBytes))
                                    }
                                }
                            }
                            // Defer writing unique index until after checks to avoid read-your-writes conflicts
                            val pendingWrites = uniqueWrites ?: ArrayList<ByteArray>().also { uniqueWrites = it }
                            pendingWrites += uniqueRef
                        }

                        val encryptedValue = encryptValueIfSensitive(dataModelId, keyBytes, reference, valueBytes)
                        setValue(tr, tableDirs, keyBytes, reference, versionBytes, encryptedValue)
                    }

                    ListSize,
                    SetSize,
                    MapSize -> {
                        val intSize = (value as Int).toVarBytes()
                        setValue(tr, tableDirs, keyBytes, reference, versionBytes, intSize)
                    }

                    TypeValue -> {
                        setTypedValue(
                            value,
                            definition,
                            tr,
                            tableDirs,
                            key,
                            reference,
                            versionBytes
                        )
                    }

                    Embed -> {
                        // Root marker for embedded value objects
                        val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, TRUE)
                        setValue(tr, tableDirs, keyBytes, reference, versionBytes, valueBytes)
                    }
                }
            }

            if (isDeleted) {
                setValue(
                    tr,
                    tableDirs,
                    keyBytes,
                    byteArrayOf(SOFT_DELETE_INDICATOR),
                    versionBytes,
                    byteArrayOf(TRUE)
                )
            }

            // Run uniqueness checks right before commit to keep all reads in txn
            checks?.forEach { it() }

            // After all checks pass, write unique index values
            uniqueWrites?.let { pendingWrites ->
                for (uniqueRef in pendingWrites) {
                    setUniqueIndexValue(tr, tableDirs, uniqueRef, versionBytes, keyBytes)
                }
            }

            persistDurableClockWatermark(tr, tableDirs, keyBytes, versionBytes)

            val finalValues = objectToAdd.change(emptyList())
            updateToEmit = Update.Addition(
                dataModel,
                key,
                writeVersion.timestamp,
                finalValues,
                isDeleted,
            )

            clusterUpdateLog?.append(
                tr = tr,
                modelId = dataModelId,
                update = ClusterLogAddition(Bytes(keyBytes), writeVersion.timestamp, finalValues, isDeleted),
            )

            AddSuccess(key, writeVersion.timestamp, emptyList())
        }
    }.also {
        if (it is AddSuccess<DM>) {
            emitUpdate(updateToEmit)
        }
    }
} catch (e: ValidationUmbrellaException) {
    ValidationFail(e)
} catch (e: ValidationException) {
    ValidationFail(listOf(e))
} catch (ue: UniqueException) {
    var indexCounter = 0
    val ref = dataModel.getPropertyReferenceByStorageBytes(
        ue.reference.size,
        { ue.reference[indexCounter++] })

    ValidationFail(
        listOf(AlreadyExistsException(ref, ue.key))
    )
} catch (t: Throwable) {
    t.rethrowIfFatal()
    val cause = t.unwrapFdb()
    ServerFail(cause.toString(), cause)
}
