package maryk.datastore.indexeddb

import kotlinx.coroutines.delay
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import maryk.core.extensions.bytes.initUInt
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationContext
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.models.migration.nextRuntimePhaseOrNull
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.datastore.shared.migration.migrationTargetIdentity
import maryk.datastore.shared.migration.requireMigrationTargetIdentity
import maryk.datastore.shared.migration.decodeStorageFormat

private const val IndexedDbStoreSchemaVersion = 3
internal val OptionsMetadataKey = byteArrayOf(1)
private val ModelsMetadataKey = byteArrayOf(2)
private val ModelDefinitionMetadataPrefix = byteArrayOf(3)
private val ModelDependentsMetadataPrefix = byteArrayOf(4)
private val MigrationStateMetadataPrefix = byteArrayOf(5)

private val SourceSchemaFormatMetadataPrefix = "maryk.storage/source-schema-format:".encodeToByteArray()

internal fun isSchemaMetadataKey(key: ByteArray): Boolean =
    key.contentEquals(OptionsMetadataKey) || key.contentEquals(StorageFormatMetadataKey) ||
        key.contentEquals(StorageFormatUpgradeStateKey) ||
        key.size == 5 && (key[0] == ModelDefinitionMetadataPrefix[0] ||
            key[0] == ModelDependentsMetadataPrefix[0] || key[0] == MigrationStateMetadataPrefix[0]) ||
        key.size >= SourceSchemaFormatMetadataPrefix.size && SourceSchemaFormatMetadataPrefix.indices.all {
            key[it] == SourceSchemaFormatMetadataPrefix[it]
        }

/** Successful-open baseline. Native revision changes trigger full metadata validation. */
internal class IndexedDbSchemaSnapshot(
    definitionKeys: List<ByteArray>,
    private val definitions: List<ByteArray?>,
    migrationStateKeys: List<ByteArray>,
    private val tracksRevision: Boolean = false,
    private var revision: ByteArray? = null,
) {
    private val metadataKeyCount = definitionKeys.size + migrationStateKeys.size
    private val keys = definitionKeys + migrationStateKeys +
        if (tracksRevision) listOf(SchemaRevisionMetadataKey) else emptyList()

    val validatedReadRevision: ByteArray? get() = revision

    /** Multi-read requests must finish in the generation validated before their first read. */
    suspend fun matchesReadRevision(store: IndexedDbByteStore, startingRevision: ByteArray?): Boolean =
        if (tracksRevision) {
            startingRevision != null && startingRevision.contentEquals(store.get("meta", SchemaRevisionMetadataKey))
        } else matches(store)

    suspend fun matches(store: IndexedDbByteStore): Boolean {
        return checkSnapshot(store, if (tracksRevision) store.get("meta", SchemaRevisionMetadataKey) else null, false)
    }

    /** Validate the generation co-read with record heads, rather than a later generation. */
    suspend fun matches(store: IndexedDbByteStore, observedRevision: ByteArray?): Boolean =
        checkSnapshot(store, observedRevision, true)

    private suspend fun checkSnapshot(
        store: IndexedDbByteStore, observedRevision: ByteArray?, requireStableRevision: Boolean,
    ): Boolean {
        if (tracksRevision && revision != null && observedRevision.contentEquals(revision)) return true
        val current = store.getBatch("meta", keys)
        val matches = definitions.indices.all { current[it].contentEquals(definitions[it]) } &&
            (definitions.size until metadataKeyCount).all { current[it] == null }
        // Large registries span native batches. Even matching bytes cannot
        // validate a mixed-generation collection: an earlier batch may already
        // have changed again by the time the revision is read.
        if (matches && tracksRevision && observedRevision.contentEquals(current.last())) {
            revision = current.last()?.copyOf()
        }
        return matches && (!tracksRevision || observedRevision.contentEquals(current.last())) &&
            (!requireStableRevision || observedRevision != null)
    }
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IndexedDbByteStore.captureSchemaSnapshot(modelIds: Set<UInt>): IndexedDbSchemaSnapshot {
    val definitionKeys = listOf(OptionsMetadataKey, StorageFormatMetadataKey) + modelIds.flatMap { modelId ->
        listOf(modelDefinitionMetadataKey(modelId), modelDependentsMetadataKey(modelId), sourceSchemaFormatMetadataKey(modelId))
    }
    val migrationKeys = listOf(StorageFormatUpgradeStateKey) + modelIds.map(::migrationStateMetadataKey)
    val tracksRevision = this is SchemaRevisionAwareIndexedDbStore
    var revisionBefore = if (tracksRevision) get("meta", SchemaRevisionMetadataKey) else null
    if (tracksRevision && revisionBefore == null) {
        revisionBefore = Uuid.random().toByteArray()
        put("meta", SchemaRevisionMetadataKey, revisionBefore)
    }
    val current = getBatch("meta", definitionKeys + migrationKeys +
        if (tracksRevision) listOf(SchemaRevisionMetadataKey) else emptyList())
    if (tracksRevision && !revisionBefore.contentEquals(current.last())) {
        throw RequestException("IndexedDB schema changed while capturing its baseline; reopen this datastore")
    }
    val noPendingMigration = (definitionKeys.size until definitionKeys.size + migrationKeys.size).all { current[it] == null }
    return IndexedDbSchemaSnapshot(
        definitionKeys,
        current.take(definitionKeys.size).map { it?.copyOf() },
        migrationKeys,
        tracksRevision,
        if (tracksRevision && noPendingMigration) current.last()?.copyOf() else null,
    )
}

internal suspend fun IndexedDbByteStore.migrateStoreMetadata(
    dataStore: IndexedDbDataStore,
    keepAllVersions: Boolean,
    keepUpdateHistoryIndex: Boolean,
    dataModelsById: Map<UInt, IsRootDataModel>,
    migrationConfiguration: MigrationConfiguration<IndexedDbDataStore>,
    versionUpdateHandler: VersionUpdateHandler<IndexedDbDataStore>?,
) {
    val optionsSummary = indexedDbOptionsSummary(keepAllVersions, keepUpdateHistoryIndex)
    val modelSummary = dataModelsById.entries
        .sortedBy { it.key }
        .joinToString(separator = "\n") { (modelId, model) -> "$modelId=${model.toIndexedDbModelSignature()}" }

    get("meta", OptionsMetadataKey)?.decodeToString()?.let { existing ->
        if (existing != optionsSummary) {
            throw RequestException(
                "IndexedDB store options differ from existing database. Existing: ${existing.compactMetadata()} Requested: ${optionsSummary.compactMetadata()}"
            )
        }
    }
    migrateIndexedDbModelDefinitions(dataStore, dataModelsById, migrationConfiguration, versionUpdateHandler)
    writeBatch(listOf(
        IndexedDbWriteOperation.Put("meta", OptionsMetadataKey, optionsSummary.encodeToByteArray()),
        IndexedDbWriteOperation.Put("meta", ModelsMetadataKey, modelSummary.encodeToByteArray()),
    ))
}

internal fun indexedDbOptionsSummary(keepAllVersions: Boolean, keepUpdateHistoryIndex: Boolean) = buildString {
        append("schemaVersion=")
        append(IndexedDbStoreSchemaVersion)
        append('\n')
        append("keepAllVersions=")
        append(keepAllVersions)
        append('\n')
        append("keepUpdateHistoryIndex=")
        append(keepUpdateHistoryIndex)
        append('\n')
}

private suspend fun IndexedDbByteStore.migrateIndexedDbModelDefinitions(
    dataStore: IndexedDbDataStore,
    dataModelsById: Map<UInt, IsRootDataModel>,
    migrationConfiguration: MigrationConfiguration<IndexedDbDataStore>,
    versionUpdateHandler: VersionUpdateHandler<IndexedDbDataStore>?,
) {
    get("meta", ModelsMetadataKey)?.decodeToString()
        ?.lineSequence()
        ?.filter { it.isNotBlank() }
        ?.associate { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) {
                throw RequestException("Invalid IndexedDB model metadata entry: $line")
            }
            line.substring(0, separator).toUInt() to line.substring(separator + 1)
        }
        .orEmpty()
        .let { legacySignatures ->
            val conversionContext = StoredModelDefinitionsContext()
            val sourceIds = scan("meta", startKey = ModelDefinitionMetadataPrefix,
                endKey = ModelDependentsMetadataPrefix, includeEnd = false).map { (key, _) ->
                if (key.size != 5) throw RequestException("Invalid stored model definition key")
                var offset = 1
                initUInt(reader = { key[offset++] })
            }
            val storedModels = loadStoredModelDefinitions((sourceIds + dataModelsById.keys).toSet()) { modelId ->
                readStoredModelDefinition(modelId, conversionContext)
            }
            // Check every pending target before any callback can write through the startup bypass.
            for ((modelId, dataModel) in dataModelsById) {
                val state = readMigrationState(modelId) ?: continue
                val storedModel = storedModels[modelId]
                if (storedModel == null ||
                    storedModel.Meta.name != dataModel.Meta.name ||
                    state.fromVersion != storedModel.Meta.version.toString() ||
                    state.toVersion != dataModel.Meta.version.toString() ||
                    state.migrationId != "${dataModel.Meta.name}:${storedModel.Meta.version}->${dataModel.Meta.version}" ||
                    dataModel.isMigrationNeeded(storedModel) !is MigrationStatus.NeedsMigration
                ) {
                    throw RequestException(
                        "IndexedDB model ${dataModel.Meta.name} has pending migration ${state.migrationId}; " +
                            "reopen with its target schema to resume before writing"
                    )
                }
                requireMigrationTargetIdentity(get("meta", migrationTargetMetadataKey(modelId)), dataModel)
            }
            for ((modelId, dataModel) in dataModelsById) {
                val storedModel = storedModels[modelId]
                if (storedModel == null) {
                    val legacySignature = legacySignatures[modelId]
                    if (legacySignature != null && legacySignature != dataModel.toIndexedDbModelSignature()) {
                        throw RequestException(
                            "IndexedDB model $modelId changed but existing metadata has no stored model definition to migrate from"
                        )
                    }
                    versionUpdateHandler?.invoke(dataStore, null, dataModel)
                    storeModelDefinition(modelId, dataModel)
                    continue
                }

                if (storedModel.Meta.name != dataModel.Meta.name) {
                    throw RequestException(
                        "IndexedDB model id $modelId is mapped to stored model \"${storedModel.Meta.name}\" but configured as \"${dataModel.Meta.name}\""
                    )
                }

                when (val status = dataModel.isMigrationNeeded(storedModel)) {
                    MigrationStatus.NewModel -> {
                        versionUpdateHandler?.invoke(dataStore, null, dataModel)
                        storeModelDefinition(modelId, dataModel)
                    }
                    MigrationStatus.AlreadyProcessed,
                    MigrationStatus.UpToDate -> Unit
                    is MigrationStatus.OnlySafeAdds -> {
                        versionUpdateHandler?.invoke(dataStore, storedModel, dataModel)
                        storeModelDefinition(modelId, dataModel)
                    }
                    is MigrationStatus.NewIndicesOnExistingProperties -> {
                        dataStore.backfillIndexRows(dataModel, status.indexesToIndex)
                        versionUpdateHandler?.invoke(dataStore, storedModel, dataModel)
                        storeModelDefinition(modelId, dataModel)
                    }
                    is MigrationStatus.NeedsMigration -> {
                        runMigration(
                            dataStore = dataStore,
                            modelId = modelId,
                            storedModel = storedModel,
                            dataModel = dataModel,
                            status = status,
                            migrationConfiguration = migrationConfiguration,
                        )
                        status.indexesToIndex?.let { dataStore.backfillIndexRows(dataModel, it) }
                        versionUpdateHandler?.invoke(dataStore, storedModel, dataModel)
                        storeModelDefinition(modelId, dataModel, completeMigration = true)
                    }
                }
            }
        }

}

private suspend fun IndexedDbByteStore.runMigration(
    dataStore: IndexedDbDataStore,
    modelId: UInt,
    storedModel: IsRootDataModel,
    dataModel: IsRootDataModel,
    status: MigrationStatus.NeedsMigration,
    migrationConfiguration: MigrationConfiguration<IndexedDbDataStore>,
) {
    if (
        migrationConfiguration.migrationExpandHandler == null &&
        migrationConfiguration.migrationHandler == null &&
        migrationConfiguration.migrationVerifyHandler == null &&
        migrationConfiguration.migrationContractHandler == null
    ) {
        throw RequestException(
            "IndexedDB model ${dataModel.Meta.name} needs migration: ${status.migrationReasons.joinToString()}"
        )
    }

    val phases = listOf(
        MigrationPhase.Expand to migrationConfiguration.migrationExpandHandler,
        MigrationPhase.Backfill to migrationConfiguration.migrationHandler,
        MigrationPhase.Verify to migrationConfiguration.migrationVerifyHandler,
        MigrationPhase.Contract to migrationConfiguration.migrationContractHandler,
    )
    val migrationId = "${dataModel.Meta.name}:${storedModel.Meta.version}->${dataModel.Meta.version}"
    val targetIdentity = migrationTargetIdentity(dataModel)
    suspend fun persistProgress(state: MigrationState) {
        writeMigrationState(modelId, state, targetIdentity)
    }
    val resumedState = readMigrationState(modelId)
    if (resumedState != null) requireMigrationTargetIdentity(get("meta", migrationTargetMetadataKey(modelId)), dataModel)
    if (resumedState != null && resumedState.migrationId != migrationId) {
        throw RequestException(
            "IndexedDB migration state for ${dataModel.Meta.name} belongs to ${resumedState.migrationId}, expected $migrationId"
        )
    }
    val startPhaseIndex = resumedState?.phase?.let { phase ->
        phases.indexOfFirst { it.first == phase }
    }?.takeIf { it >= 0 } ?: 0

    for (phaseIndex in startPhaseIndex until phases.size) {
        val (phase, handler) = phases[phaseIndex]
        var previousState = resumedState?.takeIf { it.phase == phase }
        var attempt = previousState?.attempt?.let { previousAttempt ->
            if (previousAttempt == UInt.MAX_VALUE) {
                throw RequestException(
                    "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exhausted its attempt counter"
                )
            }
            previousAttempt + 1u
        } ?: 1u
        var retryOutcomes = previousState
            ?.takeIf { it.status == MigrationStateStatus.Retry }
            ?.attempt
            ?: 0u

        while (true) {
            val maxAttempts = migrationConfiguration.migrationRetryPolicy.maxAttempts
            if (maxAttempts != null && attempt > maxAttempts) {
                throw RequestException(
                    "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exceeded max attempts"
                )
            }
            persistProgress(
                MigrationState(
                    migrationId = migrationId,
                    phase = phase,
                    status = MigrationStateStatus.Running,
                    attempt = attempt,
                    fromVersion = storedModel.Meta.version.toString(),
                    toVersion = dataModel.Meta.version.toString(),
                    cursor = previousState?.cursor,
                )
            )
            val outcome = handler?.invoke(
                MigrationContext(
                    store = dataStore,
                    storedDataModel = storedModel,
                    newDataModel = dataModel,
                    migrationStatus = status,
                    previousState = previousState,
                    attempt = attempt,
                )
            ) ?: MigrationOutcome.Success

            when (outcome) {
                MigrationOutcome.Success -> {
                    val nextPhase = phase.nextRuntimePhaseOrNull()
                    if (nextPhase != null) {
                        MigrationState(
                            migrationId = migrationId,
                            phase = nextPhase,
                            status = MigrationStateStatus.Running,
                            attempt = 0u,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                        ).also { persistProgress(it) }
                    }
                    break
                }
                is MigrationOutcome.Fatal -> {
                    persistProgress(
                        MigrationState(
                            migrationId = migrationId,
                            phase = phase,
                            status = MigrationStateStatus.Failed,
                            attempt = attempt,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                            cursor = previousState?.cursor,
                            message = outcome.reason,
                        )
                    )
                    throw RequestException(
                        "IndexedDB migration ${phase.name} failed for ${dataModel.Meta.name}: ${outcome.reason}"
                    )
                }
                is MigrationOutcome.Partial -> {
                    persistProgress(
                        MigrationState(
                            migrationId = migrationId,
                            phase = phase,
                            status = MigrationStateStatus.Partial,
                            attempt = attempt,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                            cursor = outcome.nextCursor,
                            message = outcome.message,
                        )
                    )
                    throw RequestException(
                        "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} is partial and will resume on the next open"
                    )
                }
                is MigrationOutcome.Retry -> {
                    previousState = MigrationState(
                        migrationId = migrationId,
                        phase = phase,
                        status = MigrationStateStatus.Retry,
                        attempt = attempt,
                        fromVersion = storedModel.Meta.version.toString(),
                        toVersion = dataModel.Meta.version.toString(),
                        cursor = outcome.nextCursor,
                        message = outcome.message,
                    ).also { persistProgress(it) }
                    if (
                        migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes == null &&
                        migrationConfiguration.migrationRetryPolicy.maxAttempts == null
                    ) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} returned Retry; configure a bounded retry policy or retry in a later open"
                        )
                    }
                    if (retryOutcomes == UInt.MAX_VALUE) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exhausted its retry counter"
                        )
                    }
                    retryOutcomes++
                    val maxRetryOutcomes = migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes
                    if (maxRetryOutcomes != null && retryOutcomes > maxRetryOutcomes) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exceeded max retry outcomes"
                        )
                    }
                    if (attempt == UInt.MAX_VALUE) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exhausted its attempt counter"
                        )
                    }
                    attempt++
                    outcome.retryAfterMs?.takeIf { it > 0 }?.let { delay(it) }
                }
            }
        }
    }
}

internal suspend fun IndexedDbByteStore.readStoredModelDefinition(
    modelId: UInt,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? {
    val dependentBytes = get("meta", modelDependentsMetadataKey(modelId))
    val modelBytes = get("meta", modelDefinitionMetadataKey(modelId)) ?: return null
    val schemaFormat = decodeStorageFormat(get("meta", sourceSchemaFormatMetadataKey(modelId))
        ?: get("meta", StorageFormatMetadataKey))
    // Retained roots can contain different revisions of a same-named recursive
    // dependency; never bind one pair to definitions read from another pair.
    val sourceContext = (conversionContext as? StoredModelDefinitionsContext)?.newSourcePairContext()
        ?: StorageDefinitionsConversionContext()
    fun decode(): RootDataModel<*> {
        dependentBytes?.let {
            var readIndex = 0
            Definitions.Serializer
                .readProtoBuf(dependentBytes.size, { dependentBytes[readIndex++] }, sourceContext)
                .toDataObject()
        }

        var readIndex = 0
        val storedModel = RootDataModel.Model.Serializer
            .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, sourceContext)
            .toDataObject()
        sourceContext.dataModels[storedModel.Meta.name] = DataModelReference(storedModel)
        return storedModel
    }
    val model = if (schemaFormat == StorageFormat.LEGACY) sourceContext.readLegacyTransportStorage(::decode) else decode()
    conversionContext.dataModels.putAll(sourceContext.dataModels)
    conversionContext.enums.putAll(sourceContext.enums)
    conversionContext.typeEnums.putAll(sourceContext.typeEnums)
    (conversionContext as? StoredModelDefinitionsContext)?.registerSourceRoot(model)
    return model
}

internal suspend fun IndexedDbByteStore.storeModelDefinition(
    modelId: UInt,
    dataModel: IsRootDataModel,
    completeMigration: Boolean = false,
) {
    val context = StorageDefinitionsConversionContext()
    val modelCache = WriteCache()
    val rootDataModel = dataModel as RootDataModel<*>
    val modelSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(rootDataModel, modelCache, context)
    val modelBytes = ByteArray(modelSize).also { bytes ->
        var writeIndex = 0
        RootDataModel.Model.Serializer.writeObjectProtoBuf(rootDataModel, modelCache, { bytes[writeIndex++] = it }, context)
    }

    val operations = mutableListOf<IndexedDbWriteOperation>(
        IndexedDbWriteOperation.Put("meta", modelDefinitionMetadataKey(modelId), modelBytes),
        IndexedDbWriteOperation.Put("meta", sourceSchemaFormatMetadataKey(modelId), StorageFormat.CURRENT.toString().encodeToByteArray()),
    )
    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)
    if (dependencies.isNotEmpty()) {
        val dependents = Definitions(dependencies)
        val dependentsCache = WriteCache()
        val dependentsSize = Definitions.Serializer.calculateObjectProtoBufLength(dependents, dependentsCache, context)
        val dependentsBytes = ByteArray(dependentsSize).also { bytes ->
            var writeIndex = 0
            Definitions.Serializer.writeObjectProtoBuf(dependents, dependentsCache, { bytes[writeIndex++] = it }, context)
        }
        operations += IndexedDbWriteOperation.Put("meta", modelDependentsMetadataKey(modelId), dependentsBytes)
    } else {
        operations += IndexedDbWriteOperation.Delete("meta", modelDependentsMetadataKey(modelId))
    }
    if (completeMigration) {
        operations += IndexedDbWriteOperation.Delete("meta", migrationStateMetadataKey(modelId))
        operations += IndexedDbWriteOperation.Delete("meta", migrationTargetMetadataKey(modelId))
    }
    writeBatch(operations)
}

private fun String.compactMetadata() = lineSequence()
    .filter { it.isNotBlank() }
    .joinToString(separator = ", ")

private fun modelDefinitionMetadataKey(modelId: UInt): ByteArray =
    ModelDefinitionMetadataPrefix + modelId.toBigEndianBytes()

private fun modelDependentsMetadataKey(modelId: UInt): ByteArray =
    ModelDependentsMetadataPrefix + modelId.toBigEndianBytes()

internal fun sourceSchemaFormatMetadataKey(modelId: UInt): ByteArray =
    "maryk.storage/source-schema-format:$modelId".encodeToByteArray()

internal fun migrationStateMetadataKey(modelId: UInt): ByteArray =
    MigrationStateMetadataPrefix + modelId.toBigEndianBytes()

private suspend fun IndexedDbByteStore.readMigrationState(modelId: UInt): MigrationState? {
    val bytes = get("meta", migrationStateMetadataKey(modelId)) ?: return null
    return MigrationState.requireFromPersistedBytes(bytes)
}

internal fun migrationTargetMetadataKey(modelId: UInt): ByteArray =
    "maryk.migration/target-schema:$modelId".encodeToByteArray()

private suspend fun IndexedDbByteStore.writeMigrationState(modelId: UInt, state: MigrationState, identity: ByteArray) {
    writeBatch(listOf(
        IndexedDbWriteOperation.Put("meta", migrationStateMetadataKey(modelId), state.toPersistedBytes()),
        IndexedDbWriteOperation.Put("meta", migrationTargetMetadataKey(modelId), identity),
    ))
}

private fun UInt.toBigEndianBytes(): ByteArray = ByteArray(UInt.SIZE_BYTES).also { bytes ->
    bytes[0] = (this shr 24).toByte()
    bytes[1] = (this shr 16).toByte()
    bytes[2] = (this shr 8).toByte()
    bytes[3] = this.toByte()
}

private fun IsRootDataModel.toIndexedDbModelSignature(): String = buildString {
    append(Meta.name)
    append("|version=")
    append(Meta.version)
    append("|key=")
    append(Meta.keyDefinition.referenceStorageByteArray.bytes.toHex())
    append("|minKey=")
    append(Meta.minimumKeyScanByteRange ?: "")
    append("|indexes=")
    append(Meta.indexes.orEmpty().joinToString(separator = ",") { it.indexSignature() })
    append("|properties=")
    append(propertiesSignature(this@toIndexedDbModelSignature))
}

private fun propertiesSignature(dataModel: IsValuesDataModel): String {
    val parts = mutableListOf<String>()
    collectPropertiesSignature(dataModel, null, parts, mutableListOf())
    return parts.joinToString(separator = ",")
}

private fun collectPropertiesSignature(
    dataModel: IsValuesDataModel,
    parentRef: AnyPropertyReference?,
    parts: MutableList<String>,
    modelPath: MutableList<IsValuesDataModel>,
) {
    if (modelPath.any { it === dataModel }) return
    modelPath += dataModel

    try {
        dataModel.forEach { wrapper ->
            val propertyReference = wrapper.ref(parentRef)
            val definition = wrapper.definition
            val sensitive = (wrapper as? IsSensitiveValueDefinitionWrapper<*, *, *, *>)?.sensitive == true
            val unique = (definition as? IsComparableDefinition<*, *>)?.unique == true
            parts += buildString {
                append(propertyReference.toStorageByteArray().toHex())
                append(':')
                append(wrapper.name)
                append(':')
                append(definition::class.simpleName ?: definition.toString())
                append(":required=")
                append(wrapper.required)
                append(":final=")
                append(wrapper.final)
                append(":sensitive=")
                append(sensitive)
                append(":unique=")
                append(unique)
            }

            if (definition is EmbeddedValuesDefinition<*>) {
                collectPropertiesSignature(definition.dataModel, propertyReference, parts, modelPath)
            }
        }
    } finally {
        modelPath.removeAt(modelPath.lastIndex)
    }
}

private fun IsIndexable.indexSignature(): String = when (this) {
    is IsIndexablePropertyReference<*> -> referenceStorageByteArray.bytes.toHex()
    is Multiple -> references.joinToString(separator = "+") { it.referenceStorageByteArray.bytes.toHex() }
    else -> referenceStorageByteArray.bytes.toHex()
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
