package maryk.datastore.rocksdb.model

import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

fun storeModelDefinition(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    dataModel: RootDataModelDefinition<*>
) {
    rocksDB.put(modelColumnFamily, modelNameKey, dataModel.name.encodeToByteArray())
    rocksDB.put(modelColumnFamily, modelVersionKey, dataModel.version.toByteArray())

    val context = DefinitionsConversionContext()
    val cache = WriteCache()
    val modelByteSize = RootDataModelDefinition.Model.Serializer.calculateObjectProtoBufLength(dataModel, cache, context)
    val bytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModelDefinition.Model.Serializer.writeObjectProtoBuf(dataModel, cache, { bytes[writeIndex++] = it }, context)

    rocksDB.put(modelColumnFamily, modelDefinitionKey, bytes)
}
