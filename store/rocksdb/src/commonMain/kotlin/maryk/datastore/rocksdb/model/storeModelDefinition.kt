package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

fun storeModelDefinition(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: IsRootDataModel,
) {
    rocksDB.put(modelColumnFamily, modelNameKey, dataModel.Meta.name.encodeToByteArray())
    rocksDB.put(modelColumnFamily, modelVersionKey, dataModel.Meta.version.toByteArray())

    val context = DefinitionsConversionContext()
    val cache = WriteCache()
    val modelByteSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(dataModel as RootDataModel<*>, cache, context)
    val bytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, cache, { bytes[writeIndex++] = it }, context)

    rocksDB.put(modelColumnFamily, modelDefinitionKey, bytes)

    var dependentDefinitions: Definitions
    var dependentsByteSize: Int
    var dependentsCache: WriteCache
    var contextDataModelsSize: Int

    // Continue calculating size as more dependencies are discovered
    do {
        dependentsCache = WriteCache()
        contextDataModelsSize = context.dataModels.size
        @Suppress("UNCHECKED_CAST")
        dependentDefinitions = Definitions(
            context.dataModels.values.map { it.invoke(Unit) }.filter { it !== dataModel }.reversed() as List<MarykPrimitive>
        )
        dependentsByteSize = Definitions.Serializer.calculateObjectProtoBufLength(dependentDefinitions, dependentsCache, context)
    } while (contextDataModelsSize != context.dataModels.size)

    val dependentBytes = ByteArray(dependentsByteSize)
    writeIndex = 0
    Definitions.Serializer.writeObjectProtoBuf(dependentDefinitions, dependentsCache, { dependentBytes[writeIndex++] = it }, context)

    rocksDB.put(modelColumnFamily, modelDependentDefinitionsKey, dependentBytes)
}
