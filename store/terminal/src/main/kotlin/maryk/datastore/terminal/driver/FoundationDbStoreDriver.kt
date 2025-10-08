package maryk.datastore.terminal.driver

import com.apple.foundationdb.FDB
import com.apple.foundationdb.KeySelector
import com.apple.foundationdb.directory.DirectoryLayer
import com.apple.foundationdb.directory.DirectorySubspace
import com.apple.foundationdb.Range
import com.apple.foundationdb.tuple.Tuple
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.terminal.StoreConnectionConfig

private const val DEFAULT_API_VERSION = 730

class FoundationDbStoreDriver(
    private val config: StoreConnectionConfig.FoundationDb,
) : StoreDriver {
    override val type: StoreType = StoreType.FoundationDb

    override val description: String = buildString {
        append("FoundationDB")
        config.clusterFile?.let { append(" [cluster=$it]") }
        append(" root=")
        append(config.directoryRoot.joinToString(separator = "/"))
        config.tenant?.let { tenant ->
            append(" tenant=")
            append(renderTenant(tenant))
        }
    }

    private val conversionContext = DefinitionsConversionContext()
    private val modelsByName = linkedMapOf<String, StoredModel>()
    private val keysPrefixesByModel = mutableMapOf<String, ByteArray>()

    private lateinit var fdb: FDB
    private lateinit var database: com.apple.foundationdb.Database
    private lateinit var rootDirectory: DirectorySubspace
    private lateinit var rootPath: List<String>

    private var initialized = false

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val apiVersion = config.apiVersion.takeIf { it > 0 } ?: DEFAULT_API_VERSION
            fdb = FDB.selectAPIVersion(apiVersion)
            database = if (config.clusterFile != null) {
                fdb.open(config.clusterFile)
            } else {
                fdb.open()
            }

            rootPath = (if (config.directoryRoot.isEmpty()) listOf("maryk") else config.directoryRoot).toList()
            rootDirectory = DirectoryLayer.getDefault().open(database, rootPath, null).join()
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

    private fun loadAllModels() {
        modelsByName.clear()
        keysPrefixesByModel.clear()
        val modelNames = DirectoryLayer.getDefault().list(database, rootPath).join()
        val loaded = modelNames.mapNotNull { modelName ->
            val metaPath = rootDirectory.path + listOf(modelName, "meta")
            val metaDirectory = DirectoryLayer.getDefault().open(database, metaPath, null).join()
            val keysPath = rootDirectory.path + listOf(modelName, "keys")
            val keysDirectory = DirectoryLayer.getDefault().open(database, keysPath, null).join()
            keysDirectory.pack()?.let { keysPrefixesByModel[modelName] = it }
            loadModelDefinition(modelName, metaDirectory)
        }.sortedBy { it.name }

        loaded.forEach { modelsByName[it.name] = it }
    }

    private fun loadModelDefinition(modelName: String, directory: DirectorySubspace): StoredModel? {
        val prefix = directory.pack() ?: return null
        val nameBytes = database.read { tr -> tr.get(prefix + MODEL_NAME_KEY).join() }
        val versionBytes = database.read { tr -> tr.get(prefix + MODEL_VERSION_KEY).join() }
        if (nameBytes == null || versionBytes == null) {
            return null
        }

        database.read { tr ->
            tr.get(prefix + MODEL_DEPENDENTS_KEY).join()
        }?.let { dependentsBytes ->
            var depIndex = 0
            Definitions.Serializer.readProtoBuf(
                dependentsBytes.size,
                { dependentsBytes[depIndex++] },
                conversionContext,
            ).toDataObject()
        }

        val modelBytes = database.read { tr -> tr.get(prefix + MODEL_DEFINITION_KEY).join() } ?: return null
        var modelIndex = 0
        val storedModel = RootDataModel.Model.Serializer
            .readProtoBuf(modelBytes.size, { modelBytes[modelIndex++] }, conversionContext)
            .toDataObject()

        conversionContext.dataModels[modelName] = DataModelReference(storedModel)

        var versionIndex = 0
        val version = Version.Serializer.readFromBytes { versionBytes[versionIndex++] }

        return StoredModel(modelName, version, storedModel)
    }

    private fun ensureInitialized() {
        if (!initialized) {
            throw IOException("Driver is not connected. Call connect() first.")
        }
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
        val keysPrefix = ensureKeysPrefix(name)
        val range = Range.startsWith(keysPrefix)
        val limitPlusOne = (limit.coerceAtLeast(1) + 1)
        val beginSelector = if (descending) {
            KeySelector.firstGreaterOrEqual(range.begin)
        } else {
            startAfterKey?.let { KeySelector.firstGreaterThan(keysPrefix + it) }
                ?: KeySelector.firstGreaterOrEqual(range.begin)
        }
        val endSelector = if (descending) {
            startAfterKey?.let { KeySelector.firstGreaterOrEqual(keysPrefix + it) }
                ?: KeySelector.firstGreaterOrEqual(range.end)
        } else {
            KeySelector.firstGreaterOrEqual(range.end)
        }

        val kvs = database.read { tr ->
            tr.getRange(beginSelector, endSelector, limitPlusOne, descending).asList().join()
        }
        val hasMore = kvs.size == limitPlusOne
        val visible = if (hasMore) kvs.subList(0, kvs.size - 1) else kvs
        val keys = visible.map { kv ->
            kv.key.copyOfRange(keysPrefix.size, kv.key.size)
        }
        val continuation = if (hasMore && keys.isNotEmpty()) {
            keys.last().copyOf()
        } else {
            null
        }
        ScanKeysResult(keys, continuation)
    }

    private fun ensureKeysPrefix(modelName: String): ByteArray {
        val existing = keysPrefixesByModel[modelName]
        if (existing != null) {
            return existing
        }
        val keysPath = rootDirectory.path + listOf(modelName, "keys")
        val directory = DirectoryLayer.getDefault().open(database, keysPath, null).join()
        val packed = directory.pack() ?: throw IOException("Keys directory missing for '$modelName'")
        keysPrefixesByModel[modelName] = packed
        return packed
    }

    override fun close() {
        modelsByName.clear()
        keysPrefixesByModel.clear()
        runCatching { database.close() }
    }

    companion object {
        private fun renderTenant(tuple: Tuple): String = buildString {
            append('[')
            val iterator = tuple.iterator()
            var first = true
            while (iterator.hasNext()) {
                if (!first) append(',')
                append(iterator.next())
                first = false
            }
            append(']')
        }
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(this.size + other.size)
    this.copyInto(result)
    other.copyInto(result, destinationOffset = this.size)
    return result
}
