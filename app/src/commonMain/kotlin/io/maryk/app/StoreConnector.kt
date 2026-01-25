package io.maryk.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.core.models.RootDataModel
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.model.readStoredModelDefinitionsFromDirectory
import maryk.datastore.remote.RemoteDataStore
import maryk.datastore.remote.RemoteSshConfig
import maryk.datastore.remote.RemoteStoreConfig
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.model.readStoredModelDefinitionsFromPath
import maryk.foundationdb.FDB
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectorySubspace
import maryk.foundationdb.runSuspend
import maryk.foundationdb.tuple.Tuple
import maryk.rocksdb.DBOptions
import maryk.rocksdb.Options
import maryk.datastore.shared.IsDataStore

sealed class ConnectResult {
    data class Success(val connection: StoreConnection) : ConnectResult()
    data class Error(val message: String) : ConnectResult()
}

data class StoreConnection(
    val definition: StoreDefinition,
    val dataStore: IsDataStore,
) {
    fun close() {
        runBlocking {
            runCatching { dataStore.closeAllListeners() }
            runCatching { dataStore.close() }
        }
    }
}

class StoreConnector {
    fun connect(definition: StoreDefinition): ConnectResult = when (definition.type) {
        StoreKind.ROCKS_DB -> connectRocksDb(definition)
        StoreKind.FOUNDATION_DB -> connectFoundationDb(definition)
        StoreKind.REMOTE -> connectRemote(definition)
    }

    private fun connectRocksDb(definition: StoreDefinition): ConnectResult {
        return try {
            val modelsById = loadStoredModels(definition.directory)
            val dataStore = runBlocking {
                RocksDBDataStore.open(
                    relativePath = definition.directory,
                    dataModelsById = modelsById,
                )
            }
            ConnectResult.Success(
                StoreConnection(
                    definition = definition,
                    dataStore = dataStore,
                )
            )
        } catch (e: Exception) {
            val reason = if (e.isRocksDbLockError()) {
                "RocksDB already open in another process. Close it or open a copy of the store directory."
            } else {
                e.message ?: e::class.simpleName ?: "Unknown error"
            }
            ConnectResult.Error(reason)
        }
    }

    private fun loadStoredModels(path: String): Map<UInt, RootDataModel<*>> =
        Options().use { listOptions ->
            DBOptions().use { dbOptions ->
                readStoredModelDefinitionsFromPath(path, listOptions, dbOptions)
            }
        }

    private fun connectFoundationDb(definition: StoreDefinition): ConnectResult {
        return try {
            val directoryPath = definition.directory.split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (directoryPath.isEmpty()) {
                return ConnectResult.Error("FoundationDB directory path is required.")
            }

            val modelsById = runBlocking {
                readStoredModelDefinitionsFromDirectory(
                    fdbClusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                    tenantName = definition.tenant?.let { Tuple.from(it) },
                )
            }

            val effectiveModels = modelsById.ifEmpty {
                val dir = directoryPath.joinToString("/")
                val cluster = definition.clusterFile ?: "default cluster"
                return ConnectResult.Error(
                    "No stored models found at directory `$dir` on $cluster. Check the directory path or seed data first."
                )
            }

            val keepAllVersionsPreference = runBlocking {
                detectKeepAllVersions(
                    clusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                    tenantName = definition.tenant,
                    modelNames = effectiveModels.values.map { it.Meta.name },
                ) ?: true
            }

            val dataStore = runBlocking {
                FoundationDBDataStore.open(
                    keepAllVersions = keepAllVersionsPreference,
                    fdbClusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                    tenantName = definition.tenant?.let { Tuple.from(it) },
                    dataModelsById = effectiveModels,
                )
            }

            ConnectResult.Success(
                StoreConnection(
                    definition = definition,
                    dataStore = dataStore,
                )
            )
        } catch (e: Exception) {
            ConnectResult.Error(e.message ?: e::class.simpleName ?: "Unknown error")
        } catch (t: Throwable) {
            val looksLikeMissingLib = t::class.simpleName == "UnsatisfiedLinkError" ||
                (t.message?.contains("fdb_c", ignoreCase = true) == true)
            val reason = if (looksLikeMissingLib) {
                "FoundationDB native client (libfdb_c) is missing. Install via store/foundationdb/scripts/install-foundationdb.sh or install system-wide."
            } else {
                t.message ?: t::class.simpleName ?: "Unknown error"
            }
            ConnectResult.Error(reason)
        }
    }

    private fun connectRemote(definition: StoreDefinition): ConnectResult {
        val url = definition.directory.trim()
        if (url.isBlank()) {
            return ConnectResult.Error("Remote URL is required.")
        }

        val sshConfig = definition.sshHost?.takeIf { it.isNotBlank() }?.let { host ->
            RemoteSshConfig(
                host = host,
                user = definition.sshUser?.takeIf { it.isNotBlank() },
                port = definition.sshPort ?: 22,
                localPort = definition.sshLocalPort,
                identityFile = definition.sshIdentityFile?.takeIf { it.isNotBlank() },
            )
        }

        return try {
            val dataStore = runBlocking {
                RemoteDataStore.connect(RemoteStoreConfig(baseUrl = url, ssh = sshConfig))
            }
            ConnectResult.Success(
                StoreConnection(
                    definition = definition,
                    dataStore = dataStore,
                )
            )
        } catch (e: Exception) {
            ConnectResult.Error(e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }
}

private fun Throwable.isRocksDbLockError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message?.lowercase() ?: ""
        if (message.contains("lock file") || (message.contains("lock") && message.contains("resource temporarily unavailable"))) {
            return true
        }
        current = current.cause
    }
    return false
}

private suspend fun detectKeepAllVersions(
    clusterFilePath: String?,
    directoryPath: List<String>,
    tenantName: String?,
    modelNames: List<String>,
): Boolean? {
    val modelName = modelNames.firstOrNull() ?: return null
    val fdb = FDB.selectAPIVersion(730)
    val db = if (clusterFilePath != null) fdb.open(clusterFilePath) else fdb.open()

    val tenantTuple = tenantName?.takeIf { it.isNotBlank() }?.let { Tuple.from(it) }
    val tenantDb = tenantTuple?.let { db.openTenant(it) }
    val tc: TransactionContext = tenantDb ?: db

    try {
        val rootDirectory: DirectorySubspace = try {
            withTimeout(10_000) {
                tc.runSuspend { tr ->
                    DirectoryLayer.getDefault().open(tr, directoryPath).await()
                }
            }
        } catch (_: Throwable) {
            return null
        }

        return try {
            withTimeout(10_000) {
                tc.runSuspend { tr ->
                    rootDirectory.open(tr, listOf(modelName, "table_versioned")).await()
                }
            }
            true
        } catch (t: Throwable) {
            if (t.isNoSuchDirectory()) false else null
        }
    } finally {
        tenantDb?.close()
        db.close()
    }
}

private fun Throwable.isNoSuchDirectory(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current::class.simpleName == "NoSuchDirectoryException") return true
        current = current.cause
    }
    return false
}
