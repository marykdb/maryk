package maryk.datastore.terminal.driver

import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.terminal.StoreConnectionConfig.RocksDb
import org.rocksdb.AbstractComparator
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.ComparatorOptions
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
    private val columnFamilyOptions = mutableListOf<ColumnFamilyOptions>()
    private val keysColumnFamilies = mutableMapOf<UInt, ColumnFamilyHandle>()
    private val modelIndexesByName = mutableMapOf<String, UInt>()
    private var versionedComparator: AbstractComparator? = null
    private var comparatorOptions: ComparatorOptions? = null
    private var dbOptions: DBOptions? = null
    private var initialized = false

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            RocksDB.loadLibrary()
            Options().use { options ->
                resetColumnFamilyResources()
                val columnFamilyNames = RocksDB.listColumnFamilies(options, config.path)
                if (columnFamilyNames.isEmpty()) {
                    throw IOException("No column families found in RocksDB at ${config.path}")
                }

                val descriptors = columnFamilyNames.map { createDescriptor(it) }
                val handleList = mutableListOf<ColumnFamilyHandle>()
                val optionsForDb = DBOptions().setCreateIfMissing(false)
                val openedDb = try {
                    RocksDB.openReadOnly(optionsForDb, config.path, descriptors, handleList)
                } catch (e: Throwable) {
                    runCatching { optionsForDb.close() }
                    resetColumnFamilyResources()
                    throw e
                }
                handles = handleList
                dbOptions = optionsForDb
                db = openedDb
                keysColumnFamilies.clear()
                modelColumnFamilies = descriptors.zip(handles)
                    .mapNotNull { (descriptor, handle) ->
                        when (ColumnFamilyType.fromByte(descriptor.name.firstOrNull() ?: return@mapNotNull null)) {
                            ColumnFamilyType.Keys -> {
                                val index = parseVarUInt(descriptor.name, start = 1)
                                keysColumnFamilies[index] = handle
                                null
                            }
                            ColumnFamilyType.Model -> parseModelDescriptor(descriptor.name, handle)
                            else -> null
                        }
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

    override suspend fun scanKeys(
        name: String,
        startAfterKey: ByteArray?,
        descending: Boolean,
        limit: Int,
    ): ScanKeysResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (modelsByName.isEmpty()) {
            loadAllModels()
        }
        val modelIndex = modelIndexesByName[name]
            ?: throw IOException("Model '$name' not found in store")
        val handle = keysColumnFamilies[modelIndex]
            ?: throw IOException("Keys column family missing for model '$name'")
        RocksDB.loadLibrary()
        db.newIterator(handle).use { iterator ->
            val limitPlusOne = (limit.coerceAtLeast(1) + 1)
            val keys = mutableListOf<ByteArray>()
            if (descending) {
                if (startAfterKey != null) {
                    iterator.seekForPrev(startAfterKey)
                    if (iterator.isValid && iterator.key().contentEquals(startAfterKey)) {
                        iterator.prev()
                    } else if (!iterator.isValid) {
                        iterator.seekToLast()
                    }
                } else {
                    iterator.seekToLast()
                }
                while (iterator.isValid && keys.size < limitPlusOne) {
                    keys += iterator.key().copyOf()
                    iterator.prev()
                }
            } else {
                if (startAfterKey != null) {
                    iterator.seek(startAfterKey)
                    if (iterator.isValid && iterator.key().contentEquals(startAfterKey)) {
                        iterator.next()
                    }
                } else {
                    iterator.seekToFirst()
                }
                while (iterator.isValid && keys.size < limitPlusOne) {
                    keys += iterator.key().copyOf()
                    iterator.next()
                }
            }
            val hasMore = keys.size == limitPlusOne
            val visible = if (hasMore) keys.subList(0, keys.size - 1) else keys
            val continuation = if (hasMore && visible.isNotEmpty()) {
                visible.last().copyOf()
            } else {
                null
            }
            ScanKeysResult(visible.map { it.copyOf() }, continuation)
        }
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
        modelIndexesByName.clear()
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

        modelIndexesByName[name] = entry.tableIndex

        return StoredModel(name, version, storedModel)
    }

    private fun ensureInitialized() {
        if (!initialized) {
            throw IOException("Driver is not connected. Call connect() first.")
        }
    }

    override fun close() {
        modelsByName.clear()
        modelIndexesByName.clear()
        keysColumnFamilies.clear()
        if (this::handles.isInitialized) {
            handles.forEach { handle ->
                runCatching { handle.close() }
            }
        }
        runCatching { db.close() }
        runCatching { dbOptions?.close() }
        resetColumnFamilyResources()
    }

    private fun resetColumnFamilyResources() {
        columnFamilyOptions.forEach { options ->
            runCatching { options.close() }
        }
        columnFamilyOptions.clear()
        runCatching { versionedComparator?.close() }
        versionedComparator = null
        runCatching { comparatorOptions?.close() }
        comparatorOptions = null
    }

    private fun createDescriptor(name: ByteArray): ColumnFamilyDescriptor {
        val type = name.firstOrNull()?.let(ColumnFamilyType::fromByte)
        return if (type?.requiresVersionedComparator == true) {
            val options = ColumnFamilyOptions()
            options.setComparator(ensureVersionedComparator())
            columnFamilyOptions += options
            ColumnFamilyDescriptor(name, options)
        } else {
            ColumnFamilyDescriptor(name)
        }
    }

    private fun ensureVersionedComparator(): AbstractComparator {
        val existing = versionedComparator
        if (existing != null) {
            return existing
        }
        val options = ComparatorOptions()
        comparatorOptions = options
        return MarykVersionedComparator(options).also { comparator ->
            versionedComparator = comparator
        }
    }
}

private enum class ColumnFamilyType(val marker: Byte, val requiresVersionedComparator: Boolean) {
    Model(1, false),
    Keys(2, false),
    Table(3, false),
    Index(4, false),
    Unique(5, false),
    HistoricTable(6, true),
    HistoricIndex(7, true),
    HistoricUnique(8, true);

    companion object {
        fun fromByte(marker: Byte): ColumnFamilyType? = entries.firstOrNull { it.marker == marker }
    }
}

private class MarykVersionedComparator(
    comparatorOptions: ComparatorOptions,
) : AbstractComparator(comparatorOptions) {
    override fun name(): String = "maryk.VersionedComparator"

    override fun compare(a: ByteBuffer, b: ByteBuffer): Int {
        val startA = a.position()
        val startB = b.position()
        val lengthA = a.limit() - startA
        val lengthB = b.limit() - startB
        val minLength = minOf(lengthA, lengthB)
        var index = 0
        while (index < minLength) {
            val byteA = a.get(startA + index).toInt() and 0xFF
            val byteB = b.get(startB + index).toInt() and 0xFF
            val difference = byteA - byteB
            if (difference != 0) {
                return difference
            }
            index++
        }
        return lengthA - lengthB
    }
}
