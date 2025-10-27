package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

fun readStoredModelDefinitionsById(
    dataStore: RocksDBDataStore,
): Map<UInt, RootDataModel<*>> {
    if (dataStore.dataModelsById.isEmpty()) {
        return emptyMap()
    }

    val storedNamesById = dataStore.readStoredModelNamesById()
    if (storedNamesById.isEmpty()) {
        return emptyMap()
    }

    val conversionContext = DefinitionsConversionContext()
    val storedModelsById = mutableMapOf<UInt, RootDataModel<*>>()

    for ((id, _) in storedNamesById) {
        if (!dataStore.dataModelsById.containsKey(id)) {
            continue
        }
        val columnFamily = dataStore.getColumnFamilies(id).model
        val storedModel = readStoredModelDefinition(dataStore.db, columnFamily, conversionContext)
        if (storedModel != null) {
            storedModelsById[id] = storedModel
        }
    }

    return storedModelsById
}

fun readStoredModelDefinition(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? {
    rocksDB.get(modelColumnFamily, modelDependentsDefinitionKey)?.let { dependentBytes ->
        var readIndex = 0
        Definitions.Serializer
            .readProtoBuf(dependentBytes.size, { dependentBytes[readIndex++] }, conversionContext)
            .toDataObject()
    }

    val modelBytes = rocksDB.get(modelColumnFamily, modelDefinitionKey) ?: return null

    var readIndex = 0
    val storedDataModel = RootDataModel.Model.Serializer
        .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, conversionContext)
        .toDataObject()

    conversionContext.dataModels[storedDataModel.Meta.name] = DataModelReference(storedDataModel)

    return storedDataModel
}
