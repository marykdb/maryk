package maryk.datastore.terminal.driver

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.terminal.driver.StoreConnectionConfig.RocksDb
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.DBOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB

private const val MODEL_TABLE_PREFIX: Byte = 1

private data class ModelColumnFamily(
    val tableIndex: UInt,
    val handle: ColumnFamilyHandle,
)

class RocksDbStoreDriver(
    private val config: RocksDb,
) : StoreDriver {
    override val type: StoreType = StoreType.RocksDb

    override val description: String = "RocksDB [path=${config.path}]"

    private val conversionContext = DefinitionsConversionContext()
    private val modelsByName = linkedMapOf<String, StoredModel>()

    private lateinit var db: RocksDB
    private lateinit var handles: MutableList<ColumnFamilyHandle>
    private lateinit var modelColumnFamilies: List<ModelColumnFamily>
    private var dbOptions: DBOptions? = null
    private var initialized = false

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            RocksDB.loadLibrary()
            Options().use { options ->
                val columnFamilyNames = RocksDB.listColumnFamilies(options, config.path)
                if (columnFamilyNames.isEmpty()) {
                    throw IOException("No column families found in RocksDB at ${config.path}")
                }

                val descriptors = columnFamilyNames.map { ColumnFamilyDescriptor(it) }
                handles = mutableListOf()
                val optionsForDb = DBOptions().setCreateIfMissing(false)
                dbOptions = optionsForDb
                db = RocksDB.openReadOnly(optionsForDb, config.path, descriptors, handles)
                modelColumnFamilies = descriptors.zip(handles)
                    .mapNotNull { (descriptor, handle) ->
                        parseModelDescriptor(descriptor.name, handle)
                    }
            }
        }
        initialized = true
    }

    override suspend fun listModels(): List<StoredModel> = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (modelsByName.isEmpty()) {
            loadAllModels()
        }
        modelsByName.values.toList()
    }

    override suspend fun loadModel(name: String): StoredModel? = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (modelsByName.isEmpty()) {
            loadAllModels()
        }
        modelsByName[name]
    }

    private fun parseModelDescriptor(name: ByteArray, handle: ColumnFamilyHandle): ModelColumnFamily? {
        if (name.isEmpty() || name[0] != MODEL_TABLE_PREFIX) {
            return null
        }
        val index = parseVarUInt(name, start = 1)
        return ModelColumnFamily(index, handle)
    }

    private fun parseVarUInt(bytes: ByteArray, start: Int): UInt {
        var shift = 0
        var result = 0u
        var index = start
        while (index < bytes.size) {
            val value = bytes[index].toInt() and 0xFF
            result = result or (((value and 0x7F).toUInt()) shl shift)
            if (value and 0x80 == 0) {
                return result
            }
            shift += 7
            index++
        }
        throw IOException("Malformed table index in column family name")
    }

    private fun loadAllModels() {
        modelsByName.clear()
        val loaded = modelColumnFamilies.mapNotNull { loadModelDefinition(it) }
            .sortedBy { it.name }
        loaded.forEach { modelsByName[it.name] = it }
    }

    private fun loadModelDefinition(entry: ModelColumnFamily): StoredModel? {
        val nameBytes = db.get(entry.handle, MODEL_NAME_KEY) ?: return null
        val versionBytes = db.get(entry.handle, MODEL_VERSION_KEY) ?: return null

        val name = nameBytes.decodeToString()
        var versionIndex = 0
        val version = Version.Serializer.readFromBytes { versionBytes[versionIndex++] }

        db.get(entry.handle, MODEL_DEPENDENTS_KEY)?.let { dependentBytes ->
            var depIndex = 0
            Definitions.Serializer
                .readProtoBuf(dependentBytes.size, { dependentBytes[depIndex++] }, conversionContext)
                .toDataObject()
        }

        val modelBytes = db.get(entry.handle, MODEL_DEFINITION_KEY) ?: return null
        var modelIndex = 0
        val storedModel = RootDataModel.Model.Serializer
            .readProtoBuf(modelBytes.size, { modelBytes[modelIndex++] }, conversionContext)
            .toDataObject()

        conversionContext.dataModels[name] = DataModelReference(storedModel)

        return StoredModel(name, version, storedModel)
    }

    private fun ensureInitialized() {
        if (!initialized) {
            throw IOException("Driver is not connected. Call connect() first.")
        }
    }

    override fun close() {
        modelsByName.clear()
        if (this::handles.isInitialized) {
            handles.forEach { handle ->
                runCatching { handle.close() }
            }
        }
        runCatching { db.close() }
        runCatching { dbOptions?.close() }
    }
}
