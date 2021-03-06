package maryk.datastore.rocksdb.model

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.RootDataModel.Model
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

@OptIn(ExperimentalStdlibApi::class)
fun <P: PropertyDefinitions> checkModelIfMigrationIsNeeded(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: RootDataModel<*, P>,
    onlyCheckVersion: Boolean
): MigrationStatus {
    val name = rocksDB.get(modelColumnFamily, modelNameKey)?.decodeToString()
    val version = rocksDB.get(modelColumnFamily, modelVersionKey)?.let {
        var readIndex = 0
        Version.readFromBytes { it[readIndex++] }
    }

    if (name == null || version == null) {
        return NewModel
    }

    return when {
        dataModel.version != version || !onlyCheckVersion -> {
            // Read currently stored model
            val modelBytes = rocksDB.get(modelColumnFamily, modelDefinitionKey)
                ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.name}")

            var readIndex = 0
            val context = DefinitionsConversionContext()
            @Suppress("UNCHECKED_CAST")
            val storedDataModel = Model.readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, context).toDataObject() as IsRootValuesDataModel<P>

            // Check by comparing the data models for if migration is needed
            return dataModel.isMigrationNeeded(storedDataModel)
        }
        else -> UpToDate
    }
}
