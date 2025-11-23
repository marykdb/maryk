package maryk.datastore.rocksdb.model

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

fun checkModelIfMigrationIsNeeded(
    rocksDB: RocksDB,
    modelMeta: ModelMeta?,
    modelId: UInt,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: IsRootDataModel,
    onlyCheckVersion: Boolean,
    conversionContext: DefinitionsConversionContext,
): MigrationStatus {
    val storedDefinition = rocksDB.get(modelColumnFamily, modelDefinitionKey)
    val storedNameFromModel = rocksDB.get(modelColumnFamily, modelNameKey)?.decodeToString()
    val storedVersion = rocksDB.get(modelColumnFamily, modelVersionKey)?.let {
        var readIndex = 0
        Version.Serializer.readFromBytes { it[readIndex++] }
    }

    val storedNameFromMeta = modelMeta?.name
    val storedKeySizeFromMeta = modelMeta?.keySize

    // Determine effective stored name and validate consistency
    val effectiveName = when {
        storedNameFromMeta != null && storedNameFromModel != null -> {
            if (storedNameFromMeta != storedNameFromModel) {
                throw StorageException("Model id $modelId has name \"$storedNameFromModel\" in RocksDB but \"$storedNameFromMeta\" in meta file")
            }
            storedNameFromModel
        }
        storedNameFromMeta != null -> storedNameFromMeta
        else -> storedNameFromModel
    }

    // If nothing stored yet, treat as new model
    if (storedDefinition == null || effectiveName == null || storedVersion == null) {
        return NewModel
    }

    // Validate against configured model
    if (effectiveName != dataModel.Meta.name) {
        throw StorageException("Model id $modelId is stored as \"$effectiveName\" but configured as \"${dataModel.Meta.name}\"")
    }

    // Validate key size if present in meta file
    if (storedKeySizeFromMeta != null && storedKeySizeFromMeta != dataModel.Meta.keyByteSize) {
        throw StorageException("Meta file keySize $storedKeySizeFromMeta for model id $modelId differs from configured ${dataModel.Meta.keyByteSize}")
    }

    return when {
        dataModel.Meta.version != storedVersion || !onlyCheckVersion -> {
            val storedDataModel = readStoredModelDefinition(rocksDB, modelColumnFamily, conversionContext)
                ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.Meta.name}")

            // Ensure the conversion context references the stored model by the requested name
            conversionContext.dataModels[dataModel.Meta.name] =
                conversionContext.dataModels[dataModel.Meta.name]
                    ?: DataModelReference(storedDataModel)

            dataModel.isMigrationNeeded(storedDataModel)
        }
        else -> UpToDate
    }
}
