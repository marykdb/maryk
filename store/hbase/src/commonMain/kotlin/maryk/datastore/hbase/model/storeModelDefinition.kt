package maryk.datastore.hbase.model

import kotlinx.coroutines.future.await
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.TableMetaColumns
import maryk.datastore.hbase.dataColumnFamily
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.TableDescriptor
import org.apache.hadoop.hbase.client.TableDescriptorBuilder

suspend fun HbaseDataStore.storeModelDefinition(
    admin: AsyncAdmin,
    tableDescriptor: TableDescriptor?,
    dataModel: IsRootDataModel,
    keepAllVersions: Boolean,
) {
    val newTableDescriptor = if (tableDescriptor != null) {
        TableDescriptorBuilder.newBuilder(tableDescriptor)
    } else {
        TableDescriptorBuilder.newBuilder(getTableName(dataModel))
    }

    newTableDescriptor.setValue(TableMetaColumns.Name.byteArray, dataModel.Meta.name.encodeToByteArray())
    newTableDescriptor.setValue(TableMetaColumns.Version.byteArray, dataModel.Meta.version.toByteArray())

    val context = DefinitionsConversionContext()

    val modelCache = WriteCache()

    val modelByteSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(dataModel as RootDataModel<*>, modelCache, context)

    val bytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, modelCache, { bytes[writeIndex++] = it }, context)

    newTableDescriptor.setValue(TableMetaColumns.Model.byteArray, bytes)

    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)
    // Needs to be in reverse order to do sub dependencies first and then the higher up dependencies
    dependencies.reverse()

    if (dependencies.isNotEmpty()) {
        val dependentCache = WriteCache()

        val dependents = Definitions(dependencies)
        val depModelByteSize = Definitions.Serializer.calculateObjectProtoBufLength(dependents, dependentCache, context)
        val dependentBytes = ByteArray(depModelByteSize)
        var depWriteIndex = 0
        Definitions.Serializer.writeObjectProtoBuf(dependents, dependentCache, { dependentBytes[depWriteIndex++] = it }, context)

        newTableDescriptor.setValue(TableMetaColumns.Dependents.byteArray, dependentBytes)
    }

    if (tableDescriptor != null) {
        admin.modifyTable(newTableDescriptor.build()).await()
    } else {
        println("Creating table ${dataModel.Meta.name}")
        newTableDescriptor.setColumnFamilies(listOf(
            ColumnFamilyDescriptorBuilder.newBuilder(dataColumnFamily).apply {
                if (keepAllVersions) {
                    setMaxVersions(Int.MAX_VALUE)
                    setNewVersionBehavior(true)
                }
            }.build(),
        ))
        admin.createTable(newTableDescriptor.build()).await()
    }
}
