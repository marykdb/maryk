@file:OptIn(ExperimentalEncodingApi::class)

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
import maryk.datastore.hbase.helpers.toFamilyName
import maryk.datastore.hbase.uniquesColumnFamily
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.TableDescriptor
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.slf4j.LoggerFactory
import kotlin.io.encoding.ExperimentalEncodingApi

private val logger = LoggerFactory.getLogger("HbaseDataStore")

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

    val indicesAsString = dataModel.Meta.indices?.map {
        it.toString() to it.toFamilyName()
    }

    if (tableDescriptor != null) {
        logger.info("Updating table ${dataModel.Meta.name}")

        // Add all indices which do not exist yet
        indicesAsString?.forEach { (name, familyName) ->
            if (!tableDescriptor.hasColumnFamily(familyName)) {
                logger.info("Adding index $name as ${familyName.decodeToString()}")
                newTableDescriptor.setColumnFamily(
                    createFamilyDescriptor(familyName, keepAllVersions)
                )
            }
        }

        admin.modifyTable(newTableDescriptor.build()).await()
    } else {
        logger.info("Creating table ${dataModel.Meta.name}")

        // Add all indices which do not exist yet
        indicesAsString?.forEach { (name, familyName) ->
            logger.info("Adding index $name as ${familyName.decodeToString()}")
            newTableDescriptor.setColumnFamily(
                createFamilyDescriptor(familyName, keepAllVersions)
            )
        }

        newTableDescriptor.setColumnFamily(
            createFamilyDescriptor(dataColumnFamily, keepAllVersions),
        )
        newTableDescriptor.setColumnFamily(
            createFamilyDescriptor(uniquesColumnFamily, keepAllVersions),
        )
        admin.createTable(newTableDescriptor.build()).await()
    }
}

fun createFamilyDescriptor(
    familyName: ByteArray,
    keepAllVersions: Boolean
): ColumnFamilyDescriptor? =
    ColumnFamilyDescriptorBuilder.newBuilder(familyName).apply {
        if (keepAllVersions) {
            setMaxVersions(Int.MAX_VALUE)
            setNewVersionBehavior(true)
        }
    }.build()
