package maryk.datastore.rocksdb.model

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.metadata.getStoredModelName
import maryk.datastore.rocksdb.metadata.storeModelNameMapping
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

fun checkModelIfMigrationIsNeeded(
    rocksDB: RocksDB,
    metadataColumnFamily: ColumnFamilyHandle,
    modelId: UInt,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: IsRootDataModel,
    onlyCheckVersion: Boolean,
    conversionContext: DefinitionsConversionContext,
): MigrationStatus {
    val storedNameFromMetadata = getStoredModelName(rocksDB, metadataColumnFamily, modelId)
    val name = storedNameFromMetadata ?: rocksDB.get(modelColumnFamily, modelNameKey)?.decodeToString()?.also {
        storeModelNameMapping(rocksDB, metadataColumnFamily, modelId, it)
    }
    val version = rocksDB.get(modelColumnFamily, modelVersionKey)?.let {
        var readIndex = 0
        Version.Serializer.readFromBytes { it[readIndex++] }
    }

    if (name == null || version == null) {
        return NewModel
    }

    if (name != dataModel.Meta.name) {
        throw StorageException("Model id $modelId is mapped to stored model \"$name\" but configured as \"${dataModel.Meta.name}\"")
    }

    return when {
        dataModel.Meta.version != version || !onlyCheckVersion -> {
            val storedDataModel = readStoredModelDefinition(rocksDB, modelColumnFamily, conversionContext)
                ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.Meta.name}")

            // Ensure the conversion context references the stored model by the requested name
            conversionContext.dataModels[dataModel.Meta.name] =
                conversionContext.dataModels[dataModel.Meta.name]
                    ?: DataModelReference(storedDataModel)

            // Check by comparing the data models for if migration is needed
            return dataModel.isMigrationNeeded(storedDataModel)
        }
        else -> UpToDate
    }
}
