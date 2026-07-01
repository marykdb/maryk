package maryk.datastore.indexeddb

import kotlinx.coroutines.delay
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
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.VersionUpdateHandler
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

private const val IndexedDbStoreSchemaVersion = 2
private val OptionsMetadataKey = byteArrayOf(1)
private val ModelsMetadataKey = byteArrayOf(2)
private val ModelDefinitionMetadataPrefix = byteArrayOf(3)
private val ModelDependentsMetadataPrefix = byteArrayOf(4)

internal suspend fun IndexedDbByteStore.migrateStoreMetadata(
    dataStore: IndexedDbDataStore,
    keepAllVersions: Boolean,
    keepUpdateHistoryIndex: Boolean,
    dataModelsById: Map<UInt, IsRootDataModel>,
    migrationConfiguration: MigrationConfiguration<IndexedDbDataStore>,
    versionUpdateHandler: VersionUpdateHandler<IndexedDbDataStore>?,
) {
    val optionsSummary = buildString {
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
            val conversionContext = DefinitionsConversionContext()
            for ((modelId, dataModel) in dataModelsById) {
                val storedModel = readStoredModelDefinition(modelId, conversionContext)
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
                            storedModel = storedModel,
                            dataModel = dataModel,
                            status = status,
                            migrationConfiguration = migrationConfiguration,
                        )
                        versionUpdateHandler?.invoke(dataStore, storedModel, dataModel)
                        status.indexesToIndex?.let { dataStore.backfillIndexRows(dataModel, it) }
                        storeModelDefinition(modelId, dataModel)
                    }
                }
            }
        }

    writeBatch(
        listOf(
            IndexedDbWriteOperation.Put("meta", OptionsMetadataKey, optionsSummary.encodeToByteArray()),
            IndexedDbWriteOperation.Put("meta", ModelsMetadataKey, modelSummary.encodeToByteArray()),
        )
    )
}

private suspend fun IndexedDbByteStore.runMigration(
    dataStore: IndexedDbDataStore,
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
    for ((phase, handler) in phases) {
        var attempt = 1u
        var retryOutcomes = 0u

        while (true) {
            val outcome = handler?.invoke(
                MigrationContext(
                    store = dataStore,
                    storedDataModel = storedModel,
                    newDataModel = dataModel,
                    migrationStatus = status,
                    previousState = null,
                    attempt = attempt,
                )
            ) ?: MigrationOutcome.Success

            when (outcome) {
                MigrationOutcome.Success -> break
                is MigrationOutcome.Fatal -> throw RequestException(
                    "IndexedDB migration ${phase.name} failed for ${dataModel.Meta.name}: ${outcome.reason}"
                )
                is MigrationOutcome.Partial -> throw RequestException(
                    "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} returned Partial; resume state is not persisted by this browser runtime"
                )
                is MigrationOutcome.Retry -> {
                    if (
                        migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes == null &&
                        migrationConfiguration.migrationRetryPolicy.maxAttempts == null
                    ) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} returned Retry; configure a bounded retry policy or retry in a later open"
                        )
                    }
                    retryOutcomes++
                    val maxRetryOutcomes = migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes
                    if (maxRetryOutcomes != null && retryOutcomes > maxRetryOutcomes) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exceeded max retry outcomes"
                        )
                    }
                    attempt++
                    val maxAttempts = migrationConfiguration.migrationRetryPolicy.maxAttempts
                    if (maxAttempts != null && attempt > maxAttempts) {
                        throw RequestException(
                            "IndexedDB migration ${phase.name} for ${dataModel.Meta.name} exceeded max attempts"
                        )
                    }
                    outcome.retryAfterMs?.takeIf { it > 0 }?.let { delay(it) }
                }
            }
        }
    }
}

private suspend fun IndexedDbByteStore.readStoredModelDefinition(
    modelId: UInt,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? {
    get("meta", modelDependentsMetadataKey(modelId))?.let { dependentBytes ->
        var readIndex = 0
        Definitions.Serializer
            .readProtoBuf(dependentBytes.size, { dependentBytes[readIndex++] }, conversionContext)
            .toDataObject()
    }

    val modelBytes = get("meta", modelDefinitionMetadataKey(modelId)) ?: return null
    var readIndex = 0
    val storedModel = RootDataModel.Model.Serializer
        .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, conversionContext)
        .toDataObject()

    conversionContext.dataModels[storedModel.Meta.name] = DataModelReference(storedModel)
    return storedModel
}

private suspend fun IndexedDbByteStore.storeModelDefinition(
    modelId: UInt,
    dataModel: IsRootDataModel,
) {
    val context = DefinitionsConversionContext()
    val modelCache = WriteCache()
    val rootDataModel = dataModel as RootDataModel<*>
    val modelSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(rootDataModel, modelCache, context)
    val modelBytes = ByteArray(modelSize).also { bytes ->
        var writeIndex = 0
        RootDataModel.Model.Serializer.writeObjectProtoBuf(rootDataModel, modelCache, { bytes[writeIndex++] = it }, context)
    }

    val operations = mutableListOf<IndexedDbWriteOperation>(
        IndexedDbWriteOperation.Put("meta", modelDefinitionMetadataKey(modelId), modelBytes)
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
    writeBatch(operations)
}

private fun String.compactMetadata() = lineSequence()
    .filter { it.isNotBlank() }
    .joinToString(separator = ", ")

private fun modelDefinitionMetadataKey(modelId: UInt): ByteArray =
    ModelDefinitionMetadataPrefix + modelId.toBigEndianBytes()

private fun modelDependentsMetadataKey(modelId: UInt): ByteArray =
    ModelDependentsMetadataPrefix + modelId.toBigEndianBytes()

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
