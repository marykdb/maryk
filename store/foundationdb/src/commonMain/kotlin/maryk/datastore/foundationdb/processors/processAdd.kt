package maryk.datastore.foundationdb.processors

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
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.setCreatedVersion
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.setLatestVersion
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
    val dataModelId = getDataModelId(dataModel)
    objectToAdd.validate()

    var updateToEmit: Update<DM>? = null

    runTransaction(dataModelId) { tr ->
        val packedKey = packKey(tableDirs.keysPrefix, key.bytes)
        val tombstoneKey = packKey(tableDirs.replicationTombstonePrefix, key.bytes)

        val existing = tr.get(packedKey).awaitResult()
        val tombstoneVersion = tr.get(tombstoneKey).awaitResult()?.readHLCTimestampIfExact()
        if (ignoreIfVersionNotNewer) {
            val currentVersion = if (existing != null) {
                tr.get(packKey(tableDirs.tablePrefix, key.bytes)).awaitResult()?.readHLCTimestampIfExact()
            } else null
            val lastAppliedVersion = listOfNotNull(currentVersion, tombstoneVersion).maxOrNull()
            if (lastAppliedVersion != null && version.timestamp <= lastAppliedVersion) {
                return@runTransaction AddSuccess(key, version.timestamp, emptyList())
            }
        }
        if (existing != null) {
            AlreadyExists(key)
        } else {
            val versionBytes = HLC.toStorageBytes(version)
            tr.clear(tombstoneKey)

            // Store first and last version markers
            setCreatedVersion(tr, tableDirs, key.bytes, versionBytes)
            setLatestVersion(tr, tableDirs, key.bytes, versionBytes)
            tableDirs.updateHistoryPrefix?.let { prefix ->
                tr.set(packKey(prefix, version.timestamp.toReversedVersionBytes(), key.bytes), EMPTY_BYTEARRAY)
            }

            val checks: MutableList<() -> Unit> = mutableListOf()
            val uniqueWrites: MutableList<ByteArray> = mutableListOf()

            // Indexes
            if (!isDeleted) {
                dataModel.Meta.indexes?.forEach { indexDef ->
                    val indexRef = indexDef.referenceStorageByteArray.bytes
                    indexDef.toStorageByteArraysForIndex(objectToAdd, key.bytes).forEach { valueAndKeyBytes ->
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
                            createUniqueIndexIfNotExists(dataModelId, tableDirs.uniquePrefix, reference)
                            checks += {
                                for (candidateRef in uniqueRefs) {
                                    val uniqueKey = packKey(tableDirs.uniquePrefix, candidateRef)
                                    val uniqueExists = tr.get(uniqueKey).awaitResult()
                                    if (uniqueExists?.size == VERSION_BYTE_SIZE + key.bytes.size) {
                                        val existingKeyBytes = uniqueExists.copyOfRange(
                                            VERSION_BYTE_SIZE,
                                            uniqueExists.size
                                        )
                                        throw UniqueException(reference, Key<DM>(existingKeyBytes))
                                    }
                                }
                            }
                            // Defer writing unique index until after checks to avoid read-your-writes conflicts
                            uniqueWrites += uniqueRef
                        }

                        val encryptedValue = encryptValueIfSensitive(dataModelId, key.bytes, reference, valueBytes)
                        setValue(tr, tableDirs, key.bytes, reference, versionBytes, encryptedValue)
                    }

                    ListSize,
                    SetSize,
                    MapSize -> {
                        val intSize = (value as Int).toVarBytes()
                        setValue(tr, tableDirs, key.bytes, reference, versionBytes, intSize)
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
                        setValue(tr, tableDirs, key.bytes, reference, versionBytes, valueBytes)
                    }
                }
            }

            if (isDeleted) {
                setValue(
                    tr,
                    tableDirs,
                    key.bytes,
                    byteArrayOf(SOFT_DELETE_INDICATOR),
                    versionBytes,
                    byteArrayOf(TRUE)
                )
            }

            // Run uniqueness checks right before commit to keep all reads in txn
            for (c in checks) c()

            // After all checks pass, write unique index values
            if (uniqueWrites.isNotEmpty()) {
                for (uniqueRef in uniqueWrites) {
                    setUniqueIndexValue(tr, tableDirs, uniqueRef, versionBytes, key.bytes)
                }
            }

            val finalValues = objectToAdd.change(emptyList())
            updateToEmit = Update.Addition(
                dataModel,
                key,
                version.timestamp,
                finalValues
            )

            clusterUpdateLog?.append(
                tr = tr,
                modelId = dataModelId,
                update = ClusterLogAddition(Bytes(key.bytes), version.timestamp, finalValues),
            )

            AddSuccess(key, version.timestamp, emptyList())
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
