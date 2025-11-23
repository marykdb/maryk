package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.extensions.bytes.decodeVarUInt
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableType
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.DBOptions
import maryk.rocksdb.Options
import maryk.rocksdb.RocksDB
import maryk.rocksdb.listColumnFamilies
import maryk.rocksdb.openRocksDB

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

/**
 * Open an existing RocksDB at [path], discover its column families, and read all stored model
 * definitions by id. Returns a map of id -> RootDataModel. All handles and the DB are closed before return.
 */
fun readStoredModelDefinitionsById(
    path: String,
    listOptions: Options = Options(),
    dbOptions: DBOptions = DBOptions(),
): Map<UInt, RootDataModel<*>> {
    val listedNames = listColumnFamilies(listOptions, path)
    if (listedNames.isEmpty()) return emptyMap()

    val metas = readMetaFile(path)

    val defaultName = "default".encodeToByteArray()
    val cfNames = buildList {
        listedNames.firstOrNull { it.contentEquals(defaultName) }?.let { add(it) }
        listedNames.forEach { if (!it.contentEquals(defaultName)) add(it) }
    }

    val keySizesById = metas.mapValues { it.value.keySize }

    val descriptors = cfNames.map { name ->
        val type = name.firstOrNull()?.toInt()
        val options = when (type?.toByte()) {
            TableType.HistoricTable.byte,
            TableType.HistoricIndex.byte,
            TableType.HistoricUnique.byte -> {
                val id = name.decodeVarUInt(startIndex = 1) ?: 0u
                val keySize = keySizesById[id] ?: 0
                ColumnFamilyOptions().apply {
                    setComparator(VersionedComparator(ComparatorOptions(), keySize))
                }
            }
            else -> ColumnFamilyOptions()
        }
        ColumnFamilyDescriptor(name, options)
    }
    val handles = mutableListOf<ColumnFamilyHandle>()
    val db = openRocksDB(dbOptions, path, descriptors, handles)

    try {
        val storedNamesById = metas.mapValues { it.value.name }

        val conversionContext = DefinitionsConversionContext()
        val storedModelsById = mutableMapOf<UInt, RootDataModel<*>>()

        cfNames.forEachIndexed { index, name ->
            if (name.isEmpty() || name[0] != TableType.Model.byte) return@forEachIndexed
            val id = name.decodeVarUInt(startIndex = 1) ?: return@forEachIndexed
            val modelHandle = handles[index]
            val storedModel = readStoredModelDefinition(db, modelHandle, conversionContext)
            if (storedModel != null) {
                storedModelsById[id] = storedModel
            } else {
                // debug aid: keep blank entry for visibility when debugging issues reading models
                // storedModelsById[id] = null intentionally not set
            }
        }

        // If metadata exists, keep only ids present there (for consistency) but fall back to discovered ones otherwise
        return if (storedNamesById.isNotEmpty()) {
            storedModelsById.filterKeys { storedNamesById.containsKey(it) }
        } else {
            storedModelsById
        }
    } finally {
        handles.forEach { it.close() }
        db.close()
    }
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
