package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB

fun storeModelDefinition(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: IsRootDataModel,
) {
    rocksDB.put(modelColumnFamily, modelNameKey, dataModel.Meta.name.encodeToByteArray())
    rocksDB.put(modelColumnFamily, modelVersionKey, dataModel.Meta.version.toByteArray())

    val context = DefinitionsConversionContext()

    val modelCache = WriteCache()

    val modelByteSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(dataModel as RootDataModel<*>, modelCache, context)

    val bytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, modelCache, { bytes[writeIndex++] = it }, context)

    rocksDB.put(modelColumnFamily, modelDefinitionKey, bytes)

    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)

    if (dependencies.isNotEmpty()) {
        val dependentCache = WriteCache()

        val dependents = Definitions(dependencies)
        val depModelByteSize = Definitions.Serializer.calculateObjectProtoBufLength(dependents, dependentCache, context)
        val dependentBytes = ByteArray(depModelByteSize)
        var depWriteIndex = 0
        Definitions.Serializer.writeObjectProtoBuf(dependents, dependentCache, { dependentBytes[depWriteIndex++] = it }, context)

        rocksDB.put(modelColumnFamily, modelDependentsDefinitionKey, dependentBytes)
    }
}
