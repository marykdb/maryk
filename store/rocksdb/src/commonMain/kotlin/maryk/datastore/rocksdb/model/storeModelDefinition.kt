package maryk.datastore.rocksdb.model

import maryk.core.models.IsRootDataModel
import maryk.core.models.definitions.RootDataModelDefinition
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
    val modelByteSize = RootDataModelDefinition.Model.Serializer.calculateObjectProtoBufLength(dataModel.Meta as RootDataModelDefinition<*>, cache, context)
    val bytes = ByteArray(modelByteSize)
    var writeIndex = 0
    RootDataModelDefinition.Model.Serializer.writeObjectProtoBuf(dataModel.Meta as RootDataModelDefinition<*>, cache, { bytes[writeIndex++] = it }, context)

    rocksDB.put(modelColumnFamily, modelDefinitionKey, bytes)
}
