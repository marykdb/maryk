package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.metadata.toMetadataBytes
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlin.text.encodeToByteArray

fun checkModelIfMigrationIsNeeded(
    tc: TransactionContext,
    metadataPrefix: ByteArray,
    modelId: UInt,
    model: ByteArray,
    dataModel: IsRootDataModel,
    onlyCheckModelVersion: Boolean,
    conversionContext: DefinitionsConversionContext
): MigrationStatus {
    val nameKey = packKey(model, modelNameKey)
    val versionKey = packKey(model, modelVersionKey)
    val modelIdMetadataKey = packKey(metadataPrefix, modelId.toMetadataBytes())

    // Read name and version within a single transaction
    val (name, version) = tc.run { tr ->
        val metadataFuture = tr.get(modelIdMetadataKey)
        val nameFuture = tr.get(nameKey)
        val versionFuture = tr.get(versionKey)

        val metadataName = metadataFuture.awaitResult()?.decodeToString()
        val modelStoredName = nameFuture.awaitResult()?.decodeToString()

        val resolvedName = when {
            metadataName == null && modelStoredName == null -> null
            metadataName == null -> {
                tr.set(modelIdMetadataKey, modelStoredName!!.encodeToByteArray())
                modelStoredName
            }
            modelStoredName == null -> metadataName
            metadataName != modelStoredName -> throw StorageException(
                "Model id $modelId is mapped to stored model \"$metadataName\" but metadata column contains \"$modelStoredName\""
            )
            else -> metadataName
        }

        val versionBytes = versionFuture.awaitResult()
        val readVersion = versionBytes?.let { bytes ->
            var i = 0
            Version.Serializer.readFromBytes { bytes[i++] }
        }

        resolvedName to readVersion
    }

    if (name == null || version == null) {
        return NewModel
    }

    if (name != dataModel.Meta.name) {
        throw StorageException("Model id $modelId is mapped to stored model \"$name\" but configured as \"${dataModel.Meta.name}\"")
    }

    return if (dataModel.Meta.version != version || !onlyCheckModelVersion) {
        val storedDataModel = readStoredModelDefinition(tc, model, conversionContext)
            ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.Meta.name}")

        // Ensure the conversion context references the stored model by the requested name
        val reference = conversionContext.dataModels[storedDataModel.Meta.name]
            ?: DataModelReference(storedDataModel)
        conversionContext.dataModels[dataModel.Meta.name] =
            conversionContext.dataModels[dataModel.Meta.name] ?: reference

        // Defer to the model diff to determine migration status
        dataModel.isMigrationNeeded(storedDataModel)
    } else {
        UpToDate
    }
}
