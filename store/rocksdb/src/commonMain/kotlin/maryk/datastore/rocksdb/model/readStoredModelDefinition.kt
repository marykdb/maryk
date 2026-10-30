package maryk.datastore.rocksdb.model

import maryk.core.definitions.Definitions
import maryk.core.extensions.bytes.decodeVarUInt
import maryk.core.models.RootDataModel
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext
import maryk.datastore.rocksdb.RocksDBBlockCache
import maryk.datastore.rocksdb.TableType
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.DBOptions
import maryk.rocksdb.Options
import maryk.rocksdb.RocksDB
import maryk.rocksdb.listColumnFamilies
import maryk.rocksdb.openRocksDB

/**
 * Open an existing RocksDB at [path], discover its column families, and read all stored model
 * definitions by id. Returns a map of id -> RootDataModel. All handles and the DB are closed before return.
 */
fun readStoredModelDefinitionsFromPath(
    path: String,
    listOptions: Options? = null,
    dbOptions: DBOptions? = null,
): Map<UInt, RootDataModel<*>> {
    val effectiveListOptions = listOptions ?: Options()
    val effectiveDbOptions = dbOptions ?: DBOptions()

    try {
        val listedNames = listColumnFamilies(effectiveListOptions, path)
        if (listedNames.isEmpty()) return emptyMap()

        val metas = readMetaFile(path)

        val defaultName = "default".encodeToByteArray()
        val cfNames = buildList {
            listedNames.firstOrNull { it.contentEquals(defaultName) }?.let { add(it) }
            listedNames.forEach { if (!it.contentEquals(defaultName)) add(it) }
        }

        val keySizesById = metas.mapValues { it.value.keySize }
        val blockCache = RocksDBBlockCache()

        val descriptors = cfNames.map { name ->
            val type = name.firstOrNull()?.toInt()
            val options = when (type?.toByte()) {
                TableType.HistoricTable.byte,
                TableType.HistoricIndex.byte,
                TableType.HistoricUnique.byte -> {
                    val id = name.decodeVarUInt(startIndex = 1) ?: 0u
                    val keySize = keySizesById[id] ?: 0
                    blockCache.createColumnFamilyOptions {
                        setComparator(blockCache.createVersionedComparator(keySize))
                    }
                }
                else -> blockCache.createColumnFamilyOptions()
            }
            ColumnFamilyDescriptor(name, options)
        }
        val handles = mutableListOf<ColumnFamilyHandle>()
        val db = try {
            openRocksDB(effectiveDbOptions, path, descriptors, handles)
        } catch (throwable: Throwable) {
            blockCache.close()
            throw throwable
        }

        try {
            val storedNamesById = metas.mapValues { it.value.name }

            val conversionContext = StoredModelDefinitionsContext()
            val modelHandles = cfNames.mapIndexedNotNull { index, name ->
                if (name.isEmpty() || name[0] != TableType.Model.byte) return@mapIndexedNotNull null
                val id = name.decodeVarUInt(startIndex = 1) ?: return@mapIndexedNotNull null
                id to handles[index]
            }
            val storedModelsByHandle = loadStoredModelDefinitions(modelHandles) { (_, handle) ->
                readStoredModelDefinition(db, handle, conversionContext)
            }
            val storedModelsById = storedModelsByHandle.mapKeys { (identity, _) -> identity.first }

            // If metadata exists, keep only ids present there (for consistency) but fall back to discovered ones otherwise
            return if (storedNamesById.isNotEmpty()) {
                storedModelsById.filterKeys { storedNamesById.containsKey(it) }
            } else {
                storedModelsById
            }
        } finally {
            handles.forEach { it.close() }
            db.close()
            blockCache.close()
        }
    } finally {
        if (listOptions == null) effectiveListOptions.close()
        if (dbOptions == null) effectiveDbOptions.close()
    }
}

fun readStoredModelDefinition(
    rocksDB: RocksDB,
    modelColumnFamily: ColumnFamilyHandle,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? {
    val legacyTransport = decodeStorageFormat(rocksDB.get(StorageFormatMetadataKey)) == StorageFormat.LEGACY &&
        decodeStorageFormat(rocksDB.get(modelColumnFamily, modelStorageFormatKey)) == StorageFormat.LEGACY
    // Each persisted pair is self-contained. In particular, recursive dependencies must
    // resolve within this pair, even when another retained root uses the same names.
    val sourceContext = (conversionContext as? StoredModelDefinitionsContext)?.newSourcePairContext()
        ?: StorageDefinitionsConversionContext()
    val model = if (legacyTransport) sourceContext.readLegacyTransportStorage {
        readStoredModelDefinitionPayload(rocksDB, modelColumnFamily, sourceContext)
    } else readStoredModelDefinitionPayload(rocksDB, modelColumnFamily, sourceContext)
    conversionContext.dataModels.putAll(sourceContext.dataModels)
    conversionContext.enums.putAll(sourceContext.enums)
    conversionContext.typeEnums.putAll(sourceContext.typeEnums)
    if (model != null) (conversionContext as? StoredModelDefinitionsContext)?.registerSourceRoot(model)
    return model
}

private fun readStoredModelDefinitionPayload(
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
