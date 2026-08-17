package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteBatch
import maryk.rocksdb.WriteOptions

fun storeModelDefinition(
    rocksDB: RocksDB,
    modelMetas: MutableMap<UInt, ModelMeta>,
    modelId: UInt,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: IsRootDataModel,
) {
    val context = DefinitionsConversionContext()

    val modelCache = WriteCache()
    val modelByteSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(dataModel as RootDataModel<*>, modelCache, context)
    val modelBytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, modelCache, { modelBytes[writeIndex++] = it }, context)
    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)

    val dependentBytes = if (dependencies.isNotEmpty()) {
        val dependentCache = WriteCache()

        val dependents = Definitions(dependencies)
        val depModelByteSize = Definitions.Serializer.calculateObjectProtoBufLength(dependents, dependentCache, context)
        val dependentBytes = ByteArray(depModelByteSize)
        var depWriteIndex = 0
        Definitions.Serializer.writeObjectProtoBuf(dependents, dependentCache, { dependentBytes[depWriteIndex++] = it }, context)

        dependentBytes
    } else null

    WriteBatch().use { batch ->
        batch.put(modelColumnFamily, modelNameKey, dataModel.Meta.name.encodeToByteArray())
        batch.put(modelColumnFamily, modelVersionKey, dataModel.Meta.version.toByteArray())
        batch.put(modelColumnFamily, modelDefinitionKey, modelBytes)
        if (dependentBytes != null) {
            batch.put(modelColumnFamily, modelDependentsDefinitionKey, dependentBytes)
        }
        WriteOptions().use { writeOptions ->
            rocksDB.write(writeOptions, batch)
        }
    }

    modelMetas[modelId] = ModelMeta(
        name = dataModel.Meta.name,
        keySize = dataModel.Meta.keyByteSize
    )
}
