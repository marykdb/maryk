package maryk.datastore.hbase.model

import kotlinx.coroutines.future.await
import maryk.core.definitions.Definitions
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.hbase.TableMetaColumns
import org.apache.hadoop.hbase.TableNotFoundException
import org.apache.hadoop.hbase.client.TableDescriptor
import java.util.concurrent.CompletableFuture

suspend fun checkModelIfMigrationIsNeeded(
    tableDescriptorFuture: CompletableFuture<TableDescriptor>,
    dataModel: IsRootDataModel,
    onlyCheckVersion: Boolean
): MigrationStatus {
    val tableDescriptor = try {
        tableDescriptorFuture.await()
    } catch (e: TableNotFoundException) {
        return NewModel
    }

    val name = tableDescriptor.getValue(TableMetaColumns.Name.byteArray)?.decodeToString()
    val version = tableDescriptor.getValue(TableMetaColumns.Version.byteArray)?.let {
        var readIndex = 0
        Version.Serializer.readFromBytes { it[readIndex++] }
    }

    if (name == null || version == null) {
        return NewModel
    }

    return when {
        dataModel.Meta.version != version || !onlyCheckVersion -> {
            val context = DefinitionsConversionContext()

            // Read currently stored dependent model
            tableDescriptor.getValue(TableMetaColumns.Dependents.byteArray)?.let { dependentModelBytes ->
                var readIndex = 0
                Definitions.Serializer.readProtoBuf(dependentModelBytes.size, { dependentModelBytes[readIndex++] }, context).toDataObject()
            }

            // Read currently stored model
            val modelBytes = tableDescriptor.getValue(TableMetaColumns.Model.byteArray)
                ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.Meta.name}")

            var readIndex = 0
            val storedDataModel = RootDataModel.Model.Serializer.readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, context).toDataObject()

            // Check by comparing the data models for if migration is needed
            return dataModel.isMigrationNeeded(storedDataModel)
        }
        else -> UpToDate
    }
}
